"""Python API for LinkedIn collection filtering, preparation and persistence."""
from __future__ import annotations
from contextlib import closing
from pathlib import Path
from typing import Callable
import sqlite3

from collector.extract_linkedin_readable_text_v3 import readable_text
from collector.filtering.linkedin_filter import VacancyFilter
from collector.filtering.linkedin_filter import apply_preview_decision_with_filter
from collector.filtering.linkedin_filter import content_response
from collector.filtering.linkedin_filter import load_config
from collector.filtering.publication_age import publication_age_days
from collector.logging.linkedin_logger import record_collection_event
from collector.logging.linkedin_run_logger import finish_collection_run
from collector.logging.linkedin_run_logger import record_collection_page
from collector.logging.linkedin_run_logger import start_collection_run
from collector.save_raw_page import save_content
from db.companies import get_priority_linkedin_ids
from db.filter_rejections import save_content_filter_rejection
from db.job_registry import mark_status
from db.migrate import migrate_database
from db.readable_text import save_collected_job


class CollectorService:
    """Native Python collection operations; no JSON, JPy or Java dependencies."""

    def __init__(self, driver_root: Path, db_path: Path, raw_dir: Path):
        self.driver_root = driver_root.resolve()
        self.db_path = db_path.resolve()
        self.raw_dir = raw_dir.resolve()
        migrate_database(self.db_path)
        config_path = self.driver_root.parent / "Data/Profile/Driver/collector/linkedin_preview_filter.ini"
        self.preview_config = load_config(config_path)
        self.vacancy_filter = VacancyFilter(preview_config_path=config_path, db_path=self.db_path)
        self.run_id: str | None = None

    def priority_company_ids(self) -> list:
        with closing(sqlite3.connect(self.db_path)) as connection:
            values = get_priority_linkedin_ids(connection)
        return values

    def start_run(self, run_id: str, config: dict) -> None:
        start_collection_run(run_id, config, self.db_path)
        self.run_id = run_id

    def log_page(self, run_id: str, page: dict) -> None:
        record_collection_page(run_id, page, self.db_path)

    def finish_run(self, run_id: str, status: str, stop_reason: str, accepted_count: int, message: str) -> None:
        finish_collection_run(run_id, status, stop_reason, int(accepted_count), message, self.db_path)

    def decide_preview(self, preview: dict) -> dict:
        result = apply_preview_decision_with_filter(preview, self.preview_config, self.vacancy_filter, self.db_path)
        return result

    def process_html(
        self, preview: dict, html: str, easy_apply: bool,
        cleaner: Callable[[str], str] | None = None,
    ) -> dict:
        job_id = str(preview.get("job_id") or "").strip()
        source_url = str(preview.get("source_url") or "").strip()
        title = str(preview.get("title") or "").strip()
        if not job_id or not source_url:
            raise ValueError("Browser result is missing job_id or source_url")
        age = publication_age_days(html)
        max_age = self.preview_config.getint("age", "max_days", fallback=14)
        if age is not None and age > max_age:
            reason = f"publication age {age} days exceeds {max_age} days"
            self.log_outcome(preview, "age_filtered", reason)
            return {"source": "linkedin", "job_id": job_id, "title": title,
                    "status": "age_filtered", "reason": reason}

        raw_path = save_content(
            source="linkedin",
            url=source_url,
            content=html,
            out_dir=self.raw_dir,
            ext="html",
            force=False,
            db_path=self.db_path,
            collection_method="playwright",
            collection_run_id=self.run_id,
        )
        text = readable_text("linkedin", raw_path)
        if cleaner is not None:
            text = cleaner(text)

        with closing(sqlite3.connect(self.db_path)) as connection:
            connection.execute("PRAGMA foreign_keys = ON")
            save_collected_job(
                connection,
                "linkedin",
                job_id,
                text,
                source_url=source_url,
                title=title,
                company=preview.get("company"),
                location=preview.get("location"),
                workplace=preview.get("workplace"),
                salary=preview.get("salary"),
                easy_apply=easy_apply,
            )
            filter_result = self.vacancy_filter.filter_text(title, text)
            if filter_result.rejected:
                save_content_filter_rejection(
                    connection,
                    "linkedin",
                    job_id,
                    rule=filter_result.rule,
                    matched_text=filter_result.match,
                    keyword_patterns=filter_result.keyword_patterns,
                )
                mark_status(connection, "linkedin", job_id, "CONTENT_REJECTED")
            connection.commit()

        decision = content_response(filter_result)
        status = (
            "raw_saved"
            if decision["content_decision"] == "analyze"
            else "content_filtered"
        )
        reason = str(decision.get("content_reason", ""))
        record_collection_event(
            {**preview, "status": status, "reason": reason},
            self.db_path,
        )

        return {
            "source": "linkedin",
            "job_id": job_id,
            "title": title,
            "status": status,
            "reason": reason,
        }

    def log_outcome(self, preview: dict, status: str, reason: str) -> None:
        record_collection_event({**preview, 'status': status, 'reason': reason}, self.db_path)

    def semantic_snapshot(self, source: str, job_id: str) -> dict:
        from db.semantic_duplicates import load_snapshot
        with closing(sqlite3.connect(self.db_path)) as connection:
            return load_snapshot(connection, self.run_id, source, job_id)

    def save_semantic_duplicate(self, duplicate_id: int, original_id: int) -> None:
        from db.semantic_duplicates import save_link
        with closing(sqlite3.connect(self.db_path)) as connection:
            connection.execute('PRAGMA foreign_keys = ON')
            save_link(connection, self.run_id, duplicate_id, original_id)
