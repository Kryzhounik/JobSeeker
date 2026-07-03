from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_DB = Path(os.environ.get("JOBSEEKER_PREVIEW_STATS_DB", ROOT / "data" / "jobs.sqlite"))


def apply_schema(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE IF NOT EXISTS preview_filter_counter (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            calls INTEGER NOT NULL DEFAULT 0,
            passed INTEGER NOT NULL DEFAULT 0,
            rejected INTEGER NOT NULL DEFAULT 0,
            updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        INSERT OR IGNORE INTO preview_filter_counter (
            id,
            calls,
            passed,
            rejected
        )
        VALUES (1, 0, 0, 0);

        CREATE TABLE IF NOT EXISTS preview_filter_rejections (
            title TEXT NOT NULL,
            blocked_term TEXT NOT NULL,
            count INTEGER NOT NULL DEFAULT 0,
            last_source_url TEXT,
            last_company TEXT,
            last_seen_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (title, blocked_term)
        );
        """
    )


def preview_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    preview = payload.get("preview")
    return preview if isinstance(preview, dict) else payload


def record_preview_filter(payload: Any, db_path: Path = DEFAULT_DB) -> Any:
    if isinstance(payload, list):
        for item in payload:
            record_preview_filter(item, db_path)
        return payload

    if not isinstance(payload, dict):
        return payload

    preview = preview_from_payload(payload)
    decision = str(payload.get("preview_decision") or "").strip().lower()
    blocked_terms = payload.get("preview_blocked_terms") or []
    if not isinstance(blocked_terms, list):
        blocked_terms = [str(blocked_terms)]

    db_path.parent.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(db_path) as connection:
        apply_schema(connection)
        connection.execute(
            """
            UPDATE preview_filter_counter
            SET
                calls = calls + 1,
                passed = passed + ?,
                rejected = rejected + ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = 1
            """,
            (1 if decision == "open" else 0, 1 if decision == "skip" else 0),
        )

        if decision == "skip":
            title = str(preview.get("title") or "").strip()
            source_url = str(payload.get("source_url") or preview.get("source_url") or "").strip()
            company = str(preview.get("company") or "").strip()
            for term in blocked_terms:
                blocked_term = str(term or "").strip()
                if not title or not blocked_term:
                    continue
                connection.execute(
                    """
                    INSERT INTO preview_filter_rejections (
                        title,
                        blocked_term,
                        count,
                        last_source_url,
                        last_company
                    )
                    VALUES (?, ?, 1, ?, ?)
                    ON CONFLICT(title, blocked_term) DO UPDATE SET
                        count = count + 1,
                        last_source_url = excluded.last_source_url,
                        last_company = excluded.last_company,
                        last_seen_at = CURRENT_TIMESTAMP
                    """,
                    (title, blocked_term, source_url, company),
                )

        connection.commit()

    return payload


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Log preview filter result into SQLite.")
    parser.add_argument("--input", "-i", default="-", help="Filtered preview JSON or stdin.")
    parser.add_argument("--db", default=str(DEFAULT_DB))
    args = parser.parse_args()

    if args.input == "-":
        payload = json.load(sys.stdin)
    else:
        payload = json.loads(Path(args.input).read_text(encoding="utf-8"))

    record_preview_filter(payload, Path(args.db))
    print(json.dumps(payload, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
