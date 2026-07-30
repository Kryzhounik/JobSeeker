"""SQLite persistence for Codex invocation metrics."""

from __future__ import annotations

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
    with sqlite3.connect(db_path) as connection:
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
        stats = connection.execute(
            """
            SELECT
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
                finished_at = ?,
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
            (finished_at, *stats, run_id),
        )
        connection.commit()
