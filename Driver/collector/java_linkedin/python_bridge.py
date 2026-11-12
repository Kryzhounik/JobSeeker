"""jpy-facing adapter for the existing Python collector operations.

This module is imported inside the Java process. It is not a service and has no
standalone lifecycle. The regular Python CLI entry points remain unchanged.
"""

from __future__ import annotations

import json
from pathlib import Path
import sys
from typing import Any


COLLECTOR_ROOT = Path(__file__).resolve().parent.parent
DRIVER_ROOT = COLLECTOR_ROOT.parent
for import_root in (DRIVER_ROOT, COLLECTOR_ROOT):
    if str(import_root) not in sys.path:
        sys.path.insert(0, str(import_root))

from collector.service import CollectorService


_session: CollectorService | None = None


def init_session(driver_root: str, db_path: str, raw_dir: str) -> None:
    global _session
    _session = CollectorService(Path(driver_root), Path(db_path), Path(raw_dir))


def priority_company_ids() -> str:
    return _json(_require_session().priority_company_ids())


def start_run(run_id: str, config_json: str) -> None:
    _require_session().start_run(_normalize_unicode(run_id), _object(config_json))


def log_page(run_id: str, page_json: str) -> None:
    _require_session().log_page(_normalize_unicode(run_id), _object(page_json))


def finish_run(run_id: str, status: str, stop_reason: str, accepted_count: int, message: str) -> None:
    _require_session().finish_run(_normalize_unicode(run_id), _normalize_unicode(status),
                                  _normalize_unicode(stop_reason), int(accepted_count), _normalize_unicode(message))


def decide_preview(preview_json: str) -> str:
    return _json(_require_session().decide_preview(_object(preview_json)))


def process_html(preview_json: str, html: str, easy_apply: bool, cleaner=None) -> str:
    clean_text = None if cleaner is None else lambda text: _normalize_unicode(cleaner.cleanVacancy(text))
    return _json(_require_session().process_html(
        _object(preview_json), _normalize_unicode(html), easy_apply, clean_text))


def log_outcome(preview_json: str, status: str, reason: str) -> None:
    _require_session().log_outcome(_object(preview_json), _normalize_unicode(status), _normalize_unicode(reason))


def close_session() -> None:
    global _session
    _session = None


def _require_session() -> CollectorService:
    if _session is None:
        raise RuntimeError("init_session must be called before collector operations")
    return _session


def _object(value: str) -> dict[str, Any]:
    result = json.loads(value)
    if not isinstance(result, dict):
        raise ValueError("Expected a JSON object")
    return _normalize_strings(result)


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _normalize_unicode(value: str) -> str:
    return value.encode("utf-16-le", "surrogatepass").decode("utf-16-le", "replace")


def _normalize_strings(value: Any) -> Any:
    if isinstance(value, str):
        return _normalize_unicode(value)
    if isinstance(value, dict):
        return {key: _normalize_strings(item) for key, item in value.items()}
    if isinstance(value, list):
        return [_normalize_strings(item) for item in value]
    return value
