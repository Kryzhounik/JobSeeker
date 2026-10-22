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


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def refresh_aggregates(
    connection: sqlite3.Connection,
    run_id: str,
    operation: str,
) -> None:
    """Sum the per-invocation usage already normalized by save()."""
    stats = connection.execute(
        """
        SELECT
            min(started_at), max(finished_at),
            count(*), sum(status = 'success'), sum(status = 'failed'),
            sum(input_tokens), avg(input_tokens),
            sum(cached_input_tokens), avg(cached_input_tokens),
            sum(output_tokens), avg(output_tokens),
            sum(reasoning_output_tokens), avg(reasoning_output_tokens),
            sum(weighted_tokens), avg(weighted_tokens),
            sum(estimated_credits), avg(estimated_credits)
        FROM codex_invocations
        WHERE run_id = ?
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
        """
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
            sum(input_tokens), avg(input_tokens),
            sum(cached_input_tokens), avg(cached_input_tokens),
            sum(output_tokens), avg(output_tokens),
            sum(reasoning_output_tokens), avg(reasoning_output_tokens),
            sum(weighted_tokens), avg(weighted_tokens),
            sum(estimated_credits), avg(estimated_credits)
        FROM codex_invocations
        WHERE run_id = ? AND operation = ?
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
        previous = None
        if result.thread_id:
            previous = connection.execute(
                """
                SELECT cumulative_input_tokens, cumulative_cached_input_tokens,
                       cumulative_output_tokens, cumulative_reasoning_output_tokens
                FROM codex_invocations
                WHERE run_id = ? AND thread_id = ?
                  AND (cumulative_input_tokens > 0 OR cumulative_output_tokens > 0)
                ORDER BY started_at DESC, id DESC
                LIMIT 1
                """,
                (run_id, result.thread_id),
            ).fetchone()
        cumulative = tuple(result.usage[key] for key in USAGE_KEYS)
        # A failed CLI call can have no usage snapshot (all zeros). Such rows
        # must not replace the preceding snapshot as the next call's baseline.
        if previous is None or any(
            value < previous[index] for index, value in enumerate(cumulative)
        ):
            delta = cumulative
        else:
            delta = tuple(
                value - previous[index]
                for index, value in enumerate(cumulative)
            )
        usage = dict(zip(USAGE_KEYS, delta))
        uncached = usage["input_tokens"] - usage["cached_input_tokens"]
        weighted = (
            uncached
            + usage["cached_input_tokens"] * cached_rate / input_rate
            + usage["output_tokens"] * output_rate / input_rate
        )
        credits = (
            uncached * input_rate
            + usage["cached_input_tokens"] * cached_rate
            + usage["output_tokens"] * output_rate
        ) / 1_000_000
        connection.execute(
            """
            INSERT INTO codex_invocations (
                run_id, operation, target, command, model, reasoning_effort,
                thread_id,
                started_at, finished_at, duration_ms, exit_code, status,
                input_tokens, cached_input_tokens, output_tokens,
                reasoning_output_tokens,
                cumulative_input_tokens, cumulative_cached_input_tokens,
                cumulative_output_tokens, cumulative_reasoning_output_tokens,
                input_credit_rate,
                cached_input_credit_rate, output_credit_rate,
                weighted_tokens, estimated_credits, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?, ?, ?, ?, ?)
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
                *(usage[key] for key in USAGE_KEYS),
                *cumulative,
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
