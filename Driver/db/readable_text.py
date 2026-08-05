"""Store and load analyzer-ready text for a source vacancy."""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.job_registry import mark_status
from db.job_registry import validate_identity


def has_readable_text(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
) -> bool:
    source, job_id = validate_identity(source, job_id)
    return connection.execute(
        """
        SELECT 1
        FROM source_job_texts text
        JOIN source_jobs source_job ON source_job.id = text.source_job_ref
        WHERE source_job.source = ? AND source_job.source_job_id = ?
        """,
        (source, job_id),
    ).fetchone() is not None


def save_readable_text(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
    text: str,
) -> int:
    source, job_id = validate_identity(source, job_id)
    if not text.strip():
        raise ValueError("readable_text is empty")

    source_job_ref = mark_status(connection, source, job_id, "CLEANED")
    connection.execute(
        """
        INSERT INTO source_job_texts (source_job_ref, readable_text)
        VALUES (?, ?)
        ON CONFLICT(source_job_ref) DO UPDATE SET
            readable_text = excluded.readable_text
        """,
        (source_job_ref, text),
    )
    return source_job_ref


def load_readable_text(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
) -> str:
    source, job_id = validate_identity(source, job_id)
    row = connection.execute(
        """
        SELECT text.readable_text
        FROM source_job_texts text
        JOIN source_jobs source_job ON source_job.id = text.source_job_ref
        WHERE source_job.source = ? AND source_job.source_job_id = ?
        """,
        (source, job_id),
    ).fetchone()
    if row is None:
        raise KeyError(f"Readable text not found: {source}:{job_id}")
    return str(row[0])


def main() -> None:
    from db.migrate import migrate_database

    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="strict")

    parser = argparse.ArgumentParser(description="Read one vacancy text from SQLite.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    db_path = Path(args.db)
    migrate_database(db_path)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        print(load_readable_text(connection, args.source, args.job_id), end="")


if __name__ == "__main__":
    main()
