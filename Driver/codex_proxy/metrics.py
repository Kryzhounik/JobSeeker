"""SQLite persistence for Codex invocation metrics."""

from __future__ import annotations

from contextlib import closing
from datetime import datetime, timezone
from pathlib import Path
import sqlite3
import subprocess

from codex_proxy.backend import Result
from db.migrate import migrate_database


USAGE_KEYS = (
    "input_tokens",
    "cached_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)


USAGE_DELTAS_CTE = """
WITH base_invocations AS (
    SELECT
        codex_invocations.*,
        CASE
            WHEN thread_id <> '' THEN thread_id
            ELSE 'invocation:' || id
        END AS usage_thread_id
    FROM codex_invocations
    WHERE run_id = ?
),
ordered_invocations AS (
    SELECT
        base_invocations.*,
        lag(input_tokens) OVER usage_thread AS previous_input_tokens,
        lag(cached_input_tokens) OVER usage_thread
            AS previous_cached_input_tokens,
        lag(output_tokens) OVER usage_thread AS previous_output_tokens,
        lag(reasoning_output_tokens) OVER usage_thread
            AS previous_reasoning_output_tokens
    FROM base_invocations
    WINDOW usage_thread AS (
        PARTITION BY run_id, usage_thread_id
        ORDER BY started_at, id
    )
),
usage_deltas AS (
    SELECT
        ordered_invocations.*,
        CASE
            WHEN previous_input_tokens IS NULL
                 OR input_tokens < previous_input_tokens
            THEN input_tokens
            ELSE input_tokens - previous_input_tokens
        END AS input_tokens_delta,
        CASE
            WHEN previous_cached_input_tokens IS NULL
                 OR cached_input_tokens < previous_cached_input_tokens
            THEN cached_input_tokens
            ELSE cached_input_tokens - previous_cached_input_tokens
        END AS cached_input_tokens_delta,
        CASE
            WHEN previous_output_tokens IS NULL
                 OR output_tokens < previous_output_tokens
            THEN output_tokens
            ELSE output_tokens - previous_output_tokens
        END AS output_tokens_delta,
        CASE
            WHEN previous_reasoning_output_tokens IS NULL
                 OR reasoning_output_tokens < previous_reasoning_output_tokens
            THEN reasoning_output_tokens
            ELSE reasoning_output_tokens - previous_reasoning_output_tokens
        END AS reasoning_output_tokens_delta
    FROM ordered_invocations
),
metered_invocations AS (
    SELECT
        usage_deltas.*,
        (
            input_tokens_delta - cached_input_tokens_delta
            + cached_input_tokens_delta
                * cached_input_credit_rate / input_credit_rate
            + output_tokens_delta * output_credit_rate / input_credit_rate
        ) AS weighted_tokens_delta,
        (
            (input_tokens_delta - cached_input_tokens_delta)
                * input_credit_rate
            + cached_input_tokens_delta * cached_input_credit_rate
            + output_tokens_delta * output_credit_rate
        ) / 1000000.0 AS estimated_credits_delta
    FROM usage_deltas
)
"""


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def refresh_aggregates(
    connection: sqlite3.Connection,
    run_id: str,
    operation: str,
) -> None:
    """Aggregate cumulative CLI snapshots as per-turn usage deltas."""
    stats = connection.execute(
        USAGE_DELTAS_CTE
        + """
        SELECT
            min(started_at), max(finished_at),
            count(*), sum(status = 'success'), sum(status = 'failed'),
            sum(input_tokens_delta), avg(input_tokens_delta),
            sum(cached_input_tokens_delta), avg(cached_input_tokens_delta),
            sum(output_tokens_delta), avg(output_tokens_delta),
            sum(reasoning_output_tokens_delta),
            avg(reasoning_output_tokens_delta),
            sum(weighted_tokens_delta), avg(weighted_tokens_delta),
            sum(estimated_credits_delta), avg(estimated_credits_delta)
        FROM metered_invocations
        """,
        (run_id,),
    ).fetchone()
    connection.execute(
        """
        UPDATE codex_runs SET
            started_at = ?, finished_at = ?,
            invocation_count = ?, success_count = ?, failure_count = ?,
            input_tokens_sum = ?, input_tokens_avg = ?,
            cached_input_tokens_sum = ?, cached_input_tokens_avg = ?,
            output_tokens_sum = ?, output_tokens_avg = ?,
            reasoning_output_tokens_sum = ?,
            reasoning_output_tokens_avg = ?,
            weighted_tokens_sum = ?, weighted_tokens_avg = ?,
            estimated_credits_sum = ?, estimated_credits_avg = ?
        WHERE run_id = ?
        """,
        (*stats, run_id),
    )
    connection.execute(
        USAGE_DELTAS_CTE
        + """
        INSERT OR REPLACE INTO codex_run_operations (
            run_id, operation, started_at, finished_at,
            invocation_count, success_count, failure_count,
            duration_ms_sum, duration_ms_avg,
            input_tokens_sum, input_tokens_avg,
            cached_input_tokens_sum, cached_input_tokens_avg,
            output_tokens_sum, output_tokens_avg,
            reasoning_output_tokens_sum, reasoning_output_tokens_avg,
            weighted_tokens_sum, weighted_tokens_avg,
            estimated_credits_sum, estimated_credits_avg
        )
        SELECT
            run_id, operation, min(started_at), max(finished_at),
            count(*), sum(status = 'success'), sum(status = 'failed'),
            sum(duration_ms), avg(duration_ms),
            sum(input_tokens_delta), avg(input_tokens_delta),
            sum(cached_input_tokens_delta), avg(cached_input_tokens_delta),
            sum(output_tokens_delta), avg(output_tokens_delta),
            sum(reasoning_output_tokens_delta),
            avg(reasoning_output_tokens_delta),
            sum(weighted_tokens_delta), avg(weighted_tokens_delta),
            sum(estimated_credits_delta), avg(estimated_credits_delta)
        FROM metered_invocations
        WHERE operation = ?
        GROUP BY run_id, operation
        """,
        (run_id, operation),
    )


def save(
    *,
    db_path: Path,
    run_id: str,
    operation: str,
    target: str,
    model: str,
    reasoning_effort: str,
    rates: tuple[float, float, float],
    started_at: str,
    finished_at: str,
    duration_ms: int,
    result: Result,
) -> None:
    input_rate, cached_rate, output_rate = rates
    uncached = (
        result.usage["input_tokens"]
        - result.usage["cached_input_tokens"]
    )
    weighted = (
        uncached
        + result.usage["cached_input_tokens"] * cached_rate / input_rate
        + result.usage["output_tokens"] * output_rate / input_rate
    )
    credits = (
        uncached * input_rate
        + result.usage["cached_input_tokens"] * cached_rate
        + result.usage["output_tokens"] * output_rate
    ) / 1_000_000
    status = "success" if result.exit_code == 0 else "failed"

    migrate_database(db_path)
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(
            """
            INSERT OR IGNORE INTO codex_runs (
                run_id, started_at, finished_at
            ) VALUES (?, ?, ?)
            """,
            (run_id, started_at, finished_at),
        )
        connection.execute(
            """
            INSERT INTO codex_invocations (
                run_id, operation, target, command, model, reasoning_effort,
                thread_id,
                started_at, finished_at, duration_ms, exit_code, status,
                input_tokens, cached_input_tokens, output_tokens,
                reasoning_output_tokens, input_credit_rate,
                cached_input_credit_rate, output_credit_rate,
                weighted_tokens, estimated_credits, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?)
            """,
            (
                run_id,
                operation,
                target,
                subprocess.list2cmdline(result.command),
                model,
                reasoning_effort,
                result.thread_id,
                started_at,
                finished_at,
                duration_ms,
                result.exit_code,
                status,
                *(result.usage[key] for key in USAGE_KEYS),
                input_rate,
                cached_rate,
                output_rate,
                weighted,
                credits,
                "\n".join(result.errors),
            ),
        )
        refresh_aggregates(connection, run_id, operation)
        connection.commit()
