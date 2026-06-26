from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.job_filter import evaluate_job


def project_root() -> Path:
    return ROOT


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    ensure_fitability_column(connection)
    connection.executescript(schema_path.read_text(encoding="utf-8"))


def ensure_fitability_column(connection: sqlite3.Connection) -> None:
    jobs_exists = connection.execute(
        """
        SELECT 1
        FROM sqlite_master
        WHERE type = 'table' AND name = 'jobs'
        """
    ).fetchone()
    if not jobs_exists:
        return

    columns = {
        row[1]
        for row in connection.execute("PRAGMA table_info(jobs)").fetchall()
    }
    if "fitability_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN fitability_percent INTEGER NOT NULL DEFAULT 100"
        )


def calculate_fitability(
    db_path: Path,
    schema_path: Path,
    resume_path: Path,
    filter_path: Path,
) -> list[tuple[int, int, str, str]]:
    with sqlite3.connect(db_path) as connection:
        connection.row_factory = sqlite3.Row
        apply_schema(connection, schema_path)
        jobs = connection.execute(
            """
            SELECT id, title, remote_type, remote_scope, relocation
            FROM jobs
            ORDER BY id
            """
        ).fetchall()
        updates: list[tuple[int, int, str, str]] = []

        for job in jobs:
            languages = connection.execute(
                """
                SELECT l.name AS language, jl.level, jl.level_rank
                FROM job_languages jl
                JOIN languages l ON l.id = jl.language_id
                WHERE jl.job_id = ?
                ORDER BY jl.level_rank DESC, l.name COLLATE NOCASE
                """,
                (job["id"],),
            ).fetchall()
            result = evaluate_job(
                title=job["title"],
                required_languages=languages,
                remote_type=job["remote_type"],
                remote_scope=job["remote_scope"],
                relocation=job["relocation"],
                resume_path=resume_path,
                filter_path=filter_path,
            )
            connection.execute(
                "UPDATE jobs SET fitability_percent = ? WHERE id = ?",
                (result.fitability_percent, job["id"]),
            )
            updates.append(
                (job["id"], result.fitability_percent, job["title"], result.reason)
            )

        connection.commit()
        return updates


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser(description="Calculate job fitability filters.")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "analyzer" / "db" / "schema.sql"))
    parser.add_argument("--resume", default=str(root / "common" / "config" / "resume.ini"))
    parser.add_argument("--filter-config", default=str(root / "common" / "config" / "filter.ini"))
    args = parser.parse_args()

    updates = calculate_fitability(
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        resume_path=Path(args.resume),
        filter_path=Path(args.filter_config),
    )
    for job_id, score, title, reason in updates:
        print(f"{job_id}: {score}% {title} ({reason})")


if __name__ == "__main__":
    main()
