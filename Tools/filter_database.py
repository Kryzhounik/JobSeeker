from __future__ import annotations

import argparse
import json
from pathlib import Path
import sqlite3
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "Driver"))

from collector.filtering.linkedin_filter import FilterResult
from collector.filtering.linkedin_filter import Vacancy
from collector.filtering.linkedin_filter import VacancyFilter
from db.migrate import migrate_database


DB_PATH = ROOT / "Data" / "jobs.sqlite"
PREVIEW_REJECTION_RULES = frozenset({"title_blocked", "company_blacklisted"})
CONTENT_REJECTION_RULES = frozenset(
    {"hard_blocked_technology", "hard_language_requirement"}
)
DELETE_ACTION = "DELETE"
REFILTER_ACTION_STAGE = {
    DELETE_ACTION: 1,
    "CONTENT_REJECTED": 2,
}
PREVIOUS_FILTER_STAGE = {
    "RAW": 2,
    "CONTENT_REJECTED": 2,
    "CLEANED": 3,
    "NONRELEVANT": 3,
    "ANALYZED": 3,
    "SCORED": 3,
    "SAVED": 3,
}


def calculate_refilter_action(
    previous_status: str,
    result: FilterResult,
) -> str | None:
    """Return the newly applicable earlier action, or no transition.

    Refilter is a regression view for filter configuration, not a list of all
    vacancies rejected by the current rules. Its two useful signals are that a
    new card rule now avoids downloading a vacancy that used to reach text
    processing, or that a new content rule now avoids sending a vacancy to an
    agent or analyzer. An unchanged rejection is deliberately omitted.

    These actions intentionally have different persistence semantics. A title
    or company rejection happens before collection in the normal workflow, so
    Refilter must delete the stored source row as if it had never been
    collected. A content rejection happens after text was downloaded, so the
    source row and text stay available for deduplication while only later
    analysis is discarded and the status becomes CONTENT_REJECTED.
    """
    if not result.rejected:
        return None
    if result.rule in PREVIEW_REJECTION_RULES:
        action = DELETE_ACTION
    elif result.rule in CONTENT_REJECTION_RULES:
        action = "CONTENT_REJECTED"
    else:
        raise ValueError(f"Unsupported Refilter rejection rule: {result.rule!r}")

    normalized_previous = previous_status.strip().upper()
    try:
        previous_stage = PREVIOUS_FILTER_STAGE[normalized_previous]
    except KeyError as error:
        raise ValueError(
            f"Unsupported previous processing status: {previous_status!r}"
        ) from error
    if REFILTER_ACTION_STAGE[action] >= previous_stage:
        return None
    return action


def collect_refilter_transitions(
    db_path: Path = DB_PATH,
) -> list[dict[str, Any]]:
    transitions: list[dict[str, Any]] = []
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
            action = calculate_refilter_action(
                str(processing_status),
                result,
            )
            if action is None:
                continue

            transitions.append(
                {
                    "source_job_ref": int(source_job_ref),
                    "source": str(source),
                    "source_job_id": str(source_job_id),
                    "previous_status": str(processing_status),
                    "action": action,
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

    return transitions


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description=(
            "List stored vacancies that current filters reject earlier "
            "than before."
        )
    )
    parser.add_argument("--db", default=str(DB_PATH))
    args = parser.parse_args()

    print(
        json.dumps(
            collect_refilter_transitions(Path(args.db)),
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
