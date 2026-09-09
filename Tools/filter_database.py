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
        source_jobs = database.execute(
            """
            SELECT
                source_job.id,
                source_job.source,
                source_job.source_job_id,
                source_job.processing_status,
                job.id,
                text.title,
                job.candidate_fit_percent,
                text.source_url,
                coalesce(company.name, ''),
                text.readable_text
            FROM source_job_texts AS text
            JOIN source_jobs AS source_job
                ON source_job.id = text.source_job_ref
            LEFT JOIN jobs AS job
                ON job.source_job_ref = source_job.id
            LEFT JOIN companies AS company
                ON company.id = text.company_id
            ORDER BY source_job.id
            """
        ).fetchall()

        for (
            source_job_ref,
            source,
            source_job_id,
            processing_status,
            job_id,
            title,
            fit,
            source_url,
            company,
            readable_text,
        ) in source_jobs:
            title = str(title or "")
            company = str(company or "")
            result = vacancy_filter.filter(
                Vacancy(
                    title=title,
                    text=None if readable_text is None else str(readable_text),
                    company=company,
                )
            )
            if not result.rejected:
                continue

            rejected.append(
                {
                    "source_job_ref": int(source_job_ref),
                    "source": str(source),
                    "source_job_id": str(source_job_id),
                    "scope": "Job" if job_id is not None else "Collected",
                    "stage": str(processing_status),
                    "id": None if job_id is None else int(job_id),
                    "title": title,
                    "fit": None if fit is None else int(fit),
                    "source_url": str(source_url or ""),
                    "original": result.match or title,
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
        description=(
            "List collected vacancies rejected by the current vacancy filter."
        )
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
