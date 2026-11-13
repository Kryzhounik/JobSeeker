"""LinkedIn-specific HTML preparation and age policy, independent of JPy."""
from __future__ import annotations

from typing import Callable
from contextlib import closing
import sqlite3

from collector.extract_linkedin_readable_text_v3 import readable_text
from collector.filtering.publication_age import publication_age_days
from collector.service import CollectorService
from collector.logging.linkedin_run_logger import record_collection_page
from db.companies import get_priority_linkedin_ids


class LinkedInPreparation:
    def __init__(self, service: CollectorService):
        self.service = service

    def priority_company_ids(self) -> list:
        with closing(sqlite3.connect(self.service.db_path)) as connection:
            return get_priority_linkedin_ids(connection)

    def log_page(self, run_id: str, page: dict) -> None:
        record_collection_page(run_id, page, self.service.db_path)

    def prepare_html(self, preview: dict, html: str, easy_apply: bool) -> dict:
        preview = {**preview, "source": "linkedin"}
        if not str(preview.get("job_id") or "").strip() or not str(preview.get("source_url") or "").strip():
            raise ValueError("Browser result is missing job_id or source_url")
        age = publication_age_days(html)
        max_age = self.service.preview_config.getint("age", "max_days", fallback=14)
        if age is not None and age > max_age:
            reason = f"publication age {age} days exceeds {max_age} days"
            self.service.log_outcome(preview, "age_filtered", reason)
            return {"rejection": {"source": "linkedin", "job_id": preview["job_id"],
                                  "title": preview.get("title", ""), "status": "age_filtered", "reason": reason}}
        raw_path = self.service.save_raw(preview, html, "html", "playwright")
        return {"vacancy": {"preview": preview, "description": readable_text("linkedin", raw_path),
                            "easyApply": easy_apply, "fields": {}}}

    def process_html(self, preview: dict, html: str, easy_apply: bool,
                     cleaner: Callable[[str], str] | None = None) -> dict:
        """Native Python convenience operation; Java owns its own cleaner call."""
        prepared = self.prepare_html(preview, html, easy_apply)
        if "rejection" in prepared:
            return prepared["rejection"]
        vacancy = prepared["vacancy"]
        if cleaner is not None:
            vacancy["description"] = cleaner(vacancy["description"])
        return self.service.process_vacancy(vacancy)
