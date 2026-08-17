from __future__ import annotations

import argparse
from contextlib import closing
import json
import os
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT


DEFAULT_DB = Path(os.environ.get("JOBSEEKER_PREVIEW_STATS_DB", DATA_ROOT / "jobs.sqlite"))


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
            rule TEXT NOT NULL DEFAULT 'title_blocked',
            PRIMARY KEY (title, blocked_term)
        );

        CREATE TABLE IF NOT EXISTS linkedin_collection_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            label TEXT NOT NULL DEFAULT '',
            start INTEGER,
            card_index INTEGER,
            job_id TEXT NOT NULL DEFAULT '',
            source_url TEXT NOT NULL DEFAULT '',
            title TEXT NOT NULL DEFAULT '',
            company TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL,
            reason TEXT NOT NULL DEFAULT '',
            created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_job_id
            ON linkedin_collection_events(job_id);
        CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_status
            ON linkedin_collection_events(status);
        CREATE INDEX IF NOT EXISTS idx_linkedin_collection_events_page
            ON linkedin_collection_events(label, start);
        """
    )
    columns = {
        row[1]
        for row in connection.execute(
            "PRAGMA table_info(preview_filter_rejections)"
        ).fetchall()
    }
    if "rule" not in columns:
        connection.execute(
            """
            ALTER TABLE preview_filter_rejections
            ADD COLUMN rule TEXT NOT NULL DEFAULT 'title_blocked'
            """
        )


def text(value: Any) -> str:
    return "" if value is None else str(value).strip()


def integer(value: Any) -> int | None:
    if value is None or value == "":
        return None
    return int(value)


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
    decision = text(payload.get("preview_decision")).lower()
    rule = text(payload.get("preview_rule"))
    blocked_terms = payload.get("preview_blocked_terms") or []
    if not isinstance(blocked_terms, list):
        blocked_terms = [str(blocked_terms)]

    db_path.parent.mkdir(parents=True, exist_ok=True)
    with closing(sqlite3.connect(db_path)) as connection:
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
            title = text(preview.get("title"))
            source_url = text(payload.get("source_url") or preview.get("source_url"))
            company = text(preview.get("company"))
            for term in blocked_terms:
                blocked_term = text(term)
                if not title or not blocked_term:
                    continue
                connection.execute(
                    """
                    INSERT INTO preview_filter_rejections (
                        title,
                        blocked_term,
                        count,
                        last_source_url,
                        last_company,
                        rule
                    )
                    VALUES (?, ?, 1, ?, ?, ?)
                    ON CONFLICT(title, blocked_term) DO UPDATE SET
                        count = count + 1,
                        last_source_url = excluded.last_source_url,
                        last_company = excluded.last_company,
                        rule = excluded.rule,
                        last_seen_at = CURRENT_TIMESTAMP
                    """,
                    (
                        title,
                        blocked_term,
                        source_url,
                        company,
                        rule or "unknown",
                    ),
                )

        connection.commit()

    return payload


def collection_event_from_payload(payload: dict[str, Any]) -> dict[str, Any]:
    preview = preview_from_payload(payload)
    start = payload.get("start")
    if start is None:
        start = preview.get("start")
    card_index = payload.get("card_index")
    if card_index is None:
        card_index = preview.get("index")

    return {
        "label": text(payload.get("label") or preview.get("label")),
        "start": integer(start),
        "card_index": integer(card_index),
        "job_id": text(payload.get("job_id") or preview.get("job_id")),
        "source_url": text(payload.get("source_url") or preview.get("source_url")),
        "title": text(payload.get("title") or preview.get("title")),
        "company": text(payload.get("company") or preview.get("company")),
        "status": text(payload["status"]),
        "reason": text(payload.get("reason")),
    }


def record_collection_event(payload: dict[str, Any], db_path: Path = DEFAULT_DB) -> None:
    event = collection_event_from_payload(payload)
    db_path.parent.mkdir(parents=True, exist_ok=True)
    with closing(sqlite3.connect(db_path)) as connection:
        apply_schema(connection)
        connection.execute(
            """
            INSERT INTO linkedin_collection_events (
                label,
                start,
                card_index,
                job_id,
                source_url,
                title,
                company,
                status,
                reason
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event["label"],
                event["start"],
                event["card_index"],
                event["job_id"],
                event["source_url"],
                event["title"],
                event["company"],
                event["status"],
                event["reason"],
            ),
        )
        connection.commit()


def load_json_input(path: str | None) -> dict[str, Any]:
    if path == "-":
        payload = json.load(sys.stdin)
    elif path:
        payload = json.loads(Path(path).read_text(encoding="utf-8"))
    else:
        payload = {}
    if not isinstance(payload, dict):
        raise ValueError("Logger payload must be a JSON object.")
    return payload


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Log LinkedIn collector events.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    preview = subparsers.add_parser("preview", help="Log preview filter result.")
    preview.add_argument("--input", "-i", default="-")
    preview.add_argument("--db", default=str(DEFAULT_DB))

    collection = subparsers.add_parser("collection", help="Log collection outcome.")
    collection.add_argument("--input", "-i")
    collection.add_argument("--db", default=str(DEFAULT_DB))
    for name in (
        "label",
        "start",
        "card-index",
        "job-id",
        "source-url",
        "title",
        "company",
        "status",
        "reason",
    ):
        collection.add_argument(f"--{name}")

    args = parser.parse_args()
    if args.command == "preview":
        payload = load_json_input(args.input)
        result = record_preview_filter(payload, Path(args.db))
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return

    payload = load_json_input(args.input)
    for cli_name, payload_name in (
        ("label", "label"),
        ("start", "start"),
        ("card_index", "card_index"),
        ("job_id", "job_id"),
        ("source_url", "source_url"),
        ("title", "title"),
        ("company", "company"),
        ("status", "status"),
        ("reason", "reason"),
    ):
        value = getattr(args, cli_name)
        if value is not None and value != "":
            payload[payload_name] = value
    if not text(payload.get("status")):
        raise ValueError("status is required")
    record_collection_event(payload, Path(args.db))
    print(json.dumps(collection_event_from_payload(payload), ensure_ascii=False))


if __name__ == "__main__":
    main()
