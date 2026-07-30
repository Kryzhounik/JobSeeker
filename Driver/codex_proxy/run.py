"""Transparent Codex CLI proxy with SQLite usage metrics."""

from __future__ import annotations

import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.migrate import migrate_database


MODEL_RATES = {
    "gpt-5.6-sol": (125.0, 12.5, 750.0),
    "gpt-5.6-terra": (62.5, 6.25, 375.0),
    "gpt-5.6-luna": (25.0, 2.5, 150.0),
    "gpt-5.5": (125.0, 12.5, 750.0),
    "gpt-5.4": (62.5, 6.25, 375.0),
    "gpt-5.4-mini": (18.75, 1.875, 113.0),
}
USAGE_KEYS = (
    "input_tokens",
    "cached_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)


def now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds")


def save_metrics(
    args: argparse.Namespace,
    command: list[str],
    started_at: str,
    finished_at: str,
    duration_ms: int,
    exit_code: int,
    thread_id: str,
    usage: dict[str, int],
    errors: list[str],
) -> None:
    input_rate, cached_rate, output_rate = MODEL_RATES[args.model]
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
    status = "success" if exit_code == 0 else "failed"
    db_path = Path(args.db)
    migrate_database(db_path)

    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(
            """
            INSERT OR IGNORE INTO codex_runs (run_id, started_at, finished_at)
            VALUES (?, ?, ?)
            """,
            (args.run_id, started_at, finished_at),
        )
        connection.execute(
            """
            INSERT INTO codex_invocations (
                run_id, operation, target, command, model, thread_id,
                started_at, finished_at, duration_ms, exit_code, status,
                input_tokens, cached_input_tokens, output_tokens,
                reasoning_output_tokens, input_credit_rate,
                cached_input_credit_rate, output_credit_rate,
                weighted_tokens, estimated_credits, error_message
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      ?, ?, ?)
            """,
            (
                args.run_id, args.operation, args.target,
                subprocess.list2cmdline(command), args.model, thread_id,
                started_at, finished_at, duration_ms, exit_code, status,
                *(usage[key] for key in USAGE_KEYS),
                input_rate, cached_rate, output_rate, weighted, credits,
                "\n".join(errors),
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
            (args.run_id,),
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
            (finished_at, *stats, args.run_id),
        )
        connection.commit()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--operation", required=True)
    parser.add_argument("--target", default="")
    parser.add_argument("--model", default="gpt-5.6-sol", choices=MODEL_RATES)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("codex_args", nargs=argparse.REMAINDER)
    args = parser.parse_args()
    codex_args = args.codex_args[1:] if args.codex_args[:1] == ["--"] else args.codex_args
    if not codex_args:
        raise SystemExit("Pass Codex exec arguments after --.")

    executable = shutil.which("codex.cmd") or shutil.which("codex")
    if not executable:
        raise SystemExit("Codex CLI was not found on PATH.")
    command = [executable, "exec", "--json", "--model", args.model, *codex_args]
    usage = dict.fromkeys(USAGE_KEYS, 0)
    started_at = now()
    started = time.monotonic()
    thread_id = ""
    final_message = ""
    errors: list[str] = []

    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=None,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    assert process.stdout is not None
    for line in process.stdout:
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            errors.append(line.strip())
            continue

        event_type = event.get("type")
        if event_type == "thread.started":
            thread_id = str(event.get("thread_id") or "")
        elif event_type == "turn.completed":
            values = event.get("usage") or {}
            usage = {key: int(values.get(key) or 0) for key in USAGE_KEYS}
        elif event_type == "item.completed":
            item = event.get("item") or {}
            if item.get("type") == "agent_message":
                final_message = str(item.get("text") or "")
        elif event_type in {"turn.failed", "error"}:
            errors.append(json.dumps(event, ensure_ascii=False))

    exit_code = process.wait()
    finished_at = now()
    save_metrics(
        args, command, started_at, finished_at,
        round((time.monotonic() - started) * 1000),
        exit_code, thread_id, usage, errors,
    )
    if final_message:
        print(final_message)
    raise SystemExit(exit_code)


if __name__ == "__main__":
    main()
