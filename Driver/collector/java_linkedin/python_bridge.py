"""jpy-facing adapter for the existing Python collector operations.

This module is imported inside the Java process. It is not a service and has no
standalone lifecycle. The regular Python CLI entry points remain unchanged.
"""

from __future__ import annotations

from contextlib import closing
from contextlib import redirect_stdout
from dataclasses import dataclass
import json
from pathlib import Path
import sqlite3
import sys
from typing import Any


COLLECTOR_ROOT = Path(__file__).resolve().parent.parent
DRIVER_ROOT = COLLECTOR_ROOT.parent
for import_root in (DRIVER_ROOT, COLLECTOR_ROOT):
    if str(import_root) not in sys.path:
        sys.path.insert(0, str(import_root))

from collector.extract_linkedin_readable_text_v2 import readable_text
from collector.filtering.linkedin_filter import VacancyFilter
from collector.filtering.linkedin_filter import apply_preview_decision_with_filter
from collector.filtering.linkedin_filter import content_response
from collector.filtering.linkedin_filter import load_config
from collector.logging.linkedin_logger import record_collection_event
from collector.save_raw_page import save_content
from db.companies import get_priority_linkedin_ids
from db.filter_rejections import save_content_filter_rejection
from db.migrate import migrate_database
from db.readable_text import save_collected_job


@dataclass
class Session:
    driver_root: Path
    db_path: Path
    raw_dir: Path
    preview_config: Any
    vacancy_filter: VacancyFilter


_session: Session | None = None


def init_session(driver_root: str, db_path: str, raw_dir: str) -> None:
    global _session
    root = Path(driver_root).resolve()
    database = Path(db_path).resolve()
    raw = Path(raw_dir).resolve()
    migrate_database(database)
    preview_config_path = root / "collector/filtering/linkedin_preview_filter.ini"
    _session = Session(
        driver_root=root,
        db_path=database,
        raw_dir=raw,
        preview_config=load_config(preview_config_path),
        vacancy_filter=VacancyFilter(
            preview_config_path=preview_config_path,
            db_path=database,
        ),
    )


def priority_company_ids() -> str:
    session = _require_session()
    with closing(sqlite3.connect(session.db_path)) as connection:
        values = get_priority_linkedin_ids(connection)
    return _json(values)


def decide_preview(preview_json: str) -> str:
    session = _require_session()
    preview = _object(preview_json)
    with redirect_stdout(sys.stderr):
        result = apply_preview_decision_with_filter(
            preview,
            session.preview_config,
            session.vacancy_filter,
            session.db_path,
        )
    return _json(result)


def process_html(preview_json: str, html: str) -> str:
    session = _require_session()
    preview = _object(preview_json)
    html = _normalize_unicode(html)
    job_id = str(preview.get("job_id") or "").strip()
    source_url = str(preview.get("source_url") or "").strip()
    title = str(preview.get("title") or "").strip()
    if not job_id or not source_url:
        raise ValueError("Browser result is missing job_id or source_url")

    with redirect_stdout(sys.stderr):
        raw_path = save_content(
            source="linkedin",
            url=source_url,
            content=html,
            out_dir=session.raw_dir,
            ext="html",
            force=False,
            db_path=session.db_path,
            collection_method="playwright",
        )
        text = readable_text("linkedin", raw_path)

        with closing(sqlite3.connect(session.db_path)) as connection:
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
            )
            filter_result = session.vacancy_filter.filter_text(title, text)
            if filter_result.rejected:
                save_content_filter_rejection(
                    connection,
                    "linkedin",
                    job_id,
                    rule=filter_result.rule,
                    matched_text=filter_result.match,
                    keyword_patterns=filter_result.keyword_patterns,
                )
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
            session.db_path,
        )

    return _json(
        {
            "source": "linkedin",
            "job_id": job_id,
            "title": title,
            "status": status,
            "reason": reason,
        }
    )


def log_outcome(preview_json: str, status: str, reason: str) -> None:
    session = _require_session()
    preview = _object(preview_json)
    with redirect_stdout(sys.stderr):
        record_collection_event(
            {**preview, "status": status, "reason": reason},
            session.db_path,
        )


def close_session() -> None:
    global _session
    _session = None


def _require_session() -> Session:
    if _session is None:
        raise RuntimeError("init_session must be called before collector operations")
    return _session


def _object(value: str) -> dict[str, Any]:
    result = json.loads(value)
    if not isinstance(result, dict):
        raise ValueError("Expected a JSON object")
    return result


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _normalize_unicode(value: str) -> str:
    return value.encode("utf-16-le", "surrogatepass").decode("utf-16-le", "replace")
