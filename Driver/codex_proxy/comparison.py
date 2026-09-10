"""Persist paired Desktop and CLI agent responses for later comparison."""

from __future__ import annotations

import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.migrate import migrate_database


def response_text(value: Any) -> str:
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def save_comparison(
    *,
    db_path: Path,
    operation_id: str,
    operation_type: str,
    desktop_response: Any,
    cli_response: Any,
) -> None:
    operation_id = operation_id.strip()
    operation_type = operation_type.strip()
    if not operation_id or not operation_type:
        raise ValueError("operation_id and operation_type are required")

    migrate_database(db_path)
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute(
            """
            INSERT INTO agent_operation_comparisons (
                operation_id,
                operation_type,
                desktop_response,
                cli_response
            ) VALUES (?, ?, ?, ?)
            ON CONFLICT(operation_id) DO UPDATE SET
                operation_type = excluded.operation_type,
                desktop_response = excluded.desktop_response,
                cli_response = excluded.cli_response,
                created_at = CURRENT_TIMESTAMP
            """,
            (
                operation_id,
                operation_type,
                response_text(desktop_response),
                response_text(cli_response),
            ),
        )
        connection.commit()


def main() -> None:
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8", errors="strict")

    parser = argparse.ArgumentParser()
    parser.add_argument("--operation-id", required=True)
    parser.add_argument("--operation-type", required=True)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    payload = json.load(sys.stdin)
    if not isinstance(payload, dict):
        raise ValueError("comparison input must be a JSON object")
    if "desktop_response" not in payload or "cli_response" not in payload:
        raise ValueError("both desktop_response and cli_response are required")

    save_comparison(
        db_path=Path(args.db),
        operation_id=args.operation_id,
        operation_type=args.operation_type,
        desktop_response=payload["desktop_response"],
        cli_response=payload["cli_response"],
    )
    print(args.operation_id)


if __name__ == "__main__":
    main()
