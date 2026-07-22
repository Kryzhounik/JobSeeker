"""Source-job deduplication and processing lifecycle operations.

Successful stages move forward only. A failed stage makes no lifecycle update,
so the previous successfully completed status remains visible.
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path
from urllib.parse import urlparse
import re


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT


SOURCE_CODES = ("justjoin", "linkedin")
PROCESSING_STATUSES = ("RAW", "CLEANED", "ANALYZED", "SCORED", "SAVED")
STATUS_ORDER = {status: index for index, status in enumerate(PROCESSING_STATUSES)}


def source_job_id(source: str, source_url: str) -> str:
    source = source.strip().lower()
    if source == "linkedin":
        match = re.search(r"/jobs/view/(\d+)", source_url)
        return match.group(1) if match else ""
    if source == "justjoin":
        return Path(urlparse(source_url).path).name
    return ""


def validate_identity(source: str, job_id: str) -> tuple[str, str]:
    source = source.strip().lower()
    job_id = job_id.strip()
    if source not in SOURCE_CODES:
        raise ValueError(f"Unsupported source: {source!r}")
    if not job_id:
        raise ValueError("source_job_id is empty")
    return source, job_id


def is_registered(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
) -> bool:
    source, job_id = validate_identity(source, job_id)
    return connection.execute(
        "SELECT 1 FROM source_jobs WHERE source = ? AND source_job_id = ?",
        (source, job_id),
    ).fetchone() is not None


def mark_status(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
    status: str,
) -> int:
    source, job_id = validate_identity(source, job_id)
    status = status.strip().upper()
    if status not in STATUS_ORDER:
        raise ValueError(f"Unsupported processing status: {status!r}")

    row = connection.execute(
        """
        SELECT id, processing_status
        FROM source_jobs
        WHERE source = ? AND source_job_id = ?
        """,
        (source, job_id),
    ).fetchone()
    if row is None:
        cursor = connection.execute(
            """
            INSERT INTO source_jobs (source, source_job_id, processing_status)
            VALUES (?, ?, ?)
            """,
            (source, job_id, status),
        )
        return int(cursor.lastrowid)

    registry_id, current_status = int(row[0]), str(row[1])
    if STATUS_ORDER[status] > STATUS_ORDER[current_status]:
        connection.execute(
            "UPDATE source_jobs SET processing_status = ? WHERE id = ?",
            (status, registry_id),
        )
    return registry_id


def mark_url_status(
    connection: sqlite3.Connection,
    source: str,
    source_url: str,
    status: str,
) -> int:
    return mark_status(
        connection,
        source,
        source_job_id(source, source_url),
        status,
    )


def main() -> None:
    from db.migrate import migrate_database

    parser = argparse.ArgumentParser(description="Update a source-job lifecycle status.")
    parser.add_argument("status", choices=PROCESSING_STATUSES)
    parser.add_argument("--source", required=True, choices=SOURCE_CODES)
    identity = parser.add_mutually_exclusive_group(required=True)
    identity.add_argument("--job-id")
    identity.add_argument("--url")
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    db_path = Path(args.db)
    migrate_database(db_path)
    job_id = args.job_id or source_job_id(args.source, args.url)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        mark_status(connection, args.source, job_id, args.status)
        connection.commit()
    print(f"{args.source}:{job_id} -> {args.status}")


if __name__ == "__main__":
    main()
