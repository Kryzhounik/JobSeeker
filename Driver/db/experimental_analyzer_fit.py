"""Persist the analyzer's isolated experimental candidate-fit score.

This score is never part of analyzed/scored JSON and never participates in the
official candidate-fit or aggregate score. Database views expose it only for
human comparison after the job is saved.
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.job_registry import source_job_id
from db.job_registry import validate_identity
from db.migrate import migrate_database


def validated_fit(value: int) -> int:
    fit = int(value)
    if fit < 0 or fit > 100:
        raise ValueError("analyzer fit must be between 0 and 100")
    return fit


def save_experimental_fit(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
    fit: int,
) -> int:
    source, job_id = validate_identity(source, job_id)
    fit = validated_fit(fit)
    row = connection.execute(
        """
        SELECT id
        FROM source_jobs
        WHERE source = ? AND source_job_id = ?
        """,
        (source, job_id),
    ).fetchone()
    if row is None:
        raise KeyError(f"Source job is not registered: {source}:{job_id}")

    source_job_ref = int(row[0])
    connection.execute(
        """
        INSERT INTO experimental_analyzer_fits (
            source_job_ref,
            analyzer_fit_percent
        )
        VALUES (?, ?)
        ON CONFLICT(source_job_ref) DO UPDATE SET
            analyzer_fit_percent = excluded.analyzer_fit_percent
        """,
        (source_job_ref, fit),
    )
    return source_job_ref


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Save an isolated experimental analyzer-fit score."
    )
    parser.add_argument("--source", required=True)
    parser.add_argument("--url", required=True)
    parser.add_argument("--fit", required=True, type=int)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    db_path = Path(args.db)
    migrate_database(db_path)
    job_id = source_job_id(args.source, args.url)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        save_experimental_fit(connection, args.source, job_id, args.fit)
        connection.commit()

    print(f"experimental analyzer fit: {args.source}:{job_id} -> {args.fit}")


if __name__ == "__main__":
    main()
