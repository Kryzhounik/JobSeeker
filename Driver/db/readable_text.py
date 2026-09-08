"""Store collector data and load analyzer-ready vacancy text."""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.companies import get_or_create_company
from db.job_registry import mark_status
from db.job_registry import validate_identity


WORKPLACE_VALUES = {"remote", "hybrid", "office", "unknown"}
WORKPLACE_ALIASES = {
    "on site": "office",
    "onsite": "office",
}


def normalize_collected_text(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).split())


def normalize_collected_workplace(value: Any) -> str:
    normalized = normalize_collected_text(value).lower().replace("-", " ")
    normalized = WORKPLACE_ALIASES.get(normalized, normalized)
    return normalized if normalized in WORKPLACE_VALUES else "unknown"


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


def save_collected_job(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
    text: str,
    *,
    source_url: Any,
    title: Any,
    company: Any = None,
    location: Any = None,
    workplace: Any = None,
    salary: Any = None,
) -> int:
    source, job_id = validate_identity(source, job_id)
    if not text.strip():
        raise ValueError("readable_text is empty")

    normalized_url = str(source_url or "").strip()
    normalized_title = normalize_collected_text(title)
    if not normalized_url:
        raise ValueError("source_url is empty")
    if not normalized_title:
        raise ValueError("title is empty")

    company_id = get_or_create_company(connection, company)
    source_job_ref = mark_status(connection, source, job_id, "CLEANED")
    connection.execute(
        """
        INSERT INTO source_job_texts (
            source_job_ref,
            readable_text,
            source_url,
            title,
            company_id,
            collected_location,
            collected_workplace,
            collected_salary
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(source_job_ref) DO UPDATE SET
            readable_text = excluded.readable_text,
            source_url = excluded.source_url,
            title = excluded.title,
            company_id = excluded.company_id,
            collected_location = excluded.collected_location,
            collected_workplace = excluded.collected_workplace,
            collected_salary = excluded.collected_salary
        """,
        (
            source_job_ref,
            text,
            normalized_url,
            normalized_title,
            company_id,
            normalize_collected_text(location),
            normalize_collected_workplace(workplace),
            normalize_collected_text(salary),
        ),
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
