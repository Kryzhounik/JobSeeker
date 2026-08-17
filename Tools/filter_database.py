from __future__ import annotations

import argparse
import json
from pathlib import Path
import sqlite3
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "Driver"))

from collector.filtering.linkedin_filter import Vacancy
from collector.filtering.linkedin_filter import VacancyFilter
from db.migrate import migrate_database


DB_PATH = ROOT / "Data" / "jobs.sqlite"


def collect_rejected_jobs(
    db_path: Path = DB_PATH,
) -> list[dict[str, Any]]:
    rejected: list[dict[str, Any]] = []
    migrate_database(db_path)
    vacancy_filter = VacancyFilter(db_path=db_path)

    database = sqlite3.connect(db_path)
    try:
        jobs = database.execute(
            """
            SELECT
                job.id,
                job.title,
                job.candidate_fit_percent,
                job.source_url,
                coalesce(company.name, ''),
                text.readable_text
            FROM jobs AS job
            LEFT JOIN companies AS company
                ON company.id = job.company_id
            LEFT JOIN source_job_texts AS text
                ON text.source_job_ref = job.source_job_ref
            ORDER BY job.id
            """
        ).fetchall()

        for job_id, title, fit, source_url, company, readable_text in jobs:
            result = vacancy_filter.filter(
                Vacancy(
                    title=str(title),
                    text=None if readable_text is None else str(readable_text),
                    company=str(company),
                )
            )
            if not result.rejected:
                continue

            rejected.append(
                {
                    "id": int(job_id),
                    "title": str(title),
                    "fit": int(fit or 0),
                    "source_url": str(source_url or ""),
                    "original": result.match or str(title),
                    "matched": ", ".join(
                        result.terms or result.technologies or result.languages
                    ),
                    "rule": result.rule,
                    "reason": result.reason,
                }
            )
    finally:
        database.close()

    return rejected


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="List saved jobs rejected by the current vacancy filter."
    )
    parser.add_argument("--db", default=str(DB_PATH))
    args = parser.parse_args()

    print(
        json.dumps(
            collect_rejected_jobs(Path(args.db)),
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
