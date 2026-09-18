"""Persist the effective execution settings for one analyzer run."""

from __future__ import annotations

import argparse
from contextlib import closing
from pathlib import Path
import sqlite3
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from common.paths import DATA_ROOT
from db.migrate import migrate_database


def start_analysis_run(
    run_id: str,
    parallel_agents: int,
    vacancies_per_agent: int,
    db_path: Path = DATA_ROOT / "jobs.sqlite",
) -> None:
    run_id = run_id.strip()
    if not run_id:
        raise ValueError("Analyzer run ID must not be empty")
    if parallel_agents <= 0:
        raise ValueError("parallel_agents must be positive")
    if vacancies_per_agent <= 0:
        raise ValueError("vacancies_per_agent must be positive")

    migrate_database(db_path)
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute(
            """
            INSERT INTO analyzer_runs (
                run_id,
                parallel_agents,
                vacancies_per_agent
            )
            VALUES (?, ?, ?)
            ON CONFLICT(run_id) DO UPDATE SET
                started_at = CURRENT_TIMESTAMP,
                parallel_agents = excluded.parallel_agents,
                vacancies_per_agent = excluded.vacancies_per_agent
            """,
            (run_id, parallel_agents, vacancies_per_agent),
        )
        connection.commit()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Persist effective analyzer execution settings."
    )
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--parallel-agents", required=True, type=int)
    parser.add_argument("--vacancies-per-agent", required=True, type=int)
    parser.add_argument("--db", type=Path, default=DATA_ROOT / "jobs.sqlite")
    args = parser.parse_args()

    start_analysis_run(
        run_id=args.run_id,
        parallel_agents=args.parallel_agents,
        vacancies_per_agent=args.vacancies_per_agent,
        db_path=args.db,
    )


if __name__ == "__main__":
    main()
