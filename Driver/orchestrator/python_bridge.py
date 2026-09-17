"""jpy adapter for agent operations owned by the Java orchestrator."""

from __future__ import annotations

from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
from typing import Any


DRIVER_ROOT = Path(__file__).resolve().parent.parent
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.metrics_proxy import run
from db.job_registry import mark_status
from db.migrate import migrate_database


def run_agent_filter(run_id: str, scope_json: str, db_path: str) -> str:
    """Run the existing title filter once through the metered Codex CLI."""
    scope = json.loads(scope_json)
    if not isinstance(scope, list):
        raise ValueError("Agent filter scope must be a JSON array")

    result = run(
        run_id=run_id,
        operation="agent_filter",
        target="linkedin:batch",
        instruction_path=DRIVER_ROOT / "analyzer" / "agent_filter.md",
        input_text=_json(scope),
        input_name="collected_scope.json",
        context_paths=(),
        output_schema=(
            DRIVER_ROOT / "contracts" / "agent_filter_result.schema.json"
        ),
        db_path=Path(db_path),
    )
    if result.exit_code != 0:
        details = "; ".join(result.errors) or "Codex CLI exited unsuccessfully"
        raise RuntimeError(f"Agent filter failed: {details}")
    if not result.final_message.strip():
        raise RuntimeError("Agent filter returned an empty response")
    return result.final_message


def mark_nonrelevant(db_path: str, source: str, job_ids_json: str) -> None:
    """Persist validated filter decisions atomically."""
    job_ids = json.loads(job_ids_json)
    if not isinstance(job_ids, list) or not all(
        isinstance(job_id, str) and job_id for job_id in job_ids
    ):
        raise ValueError("job_ids must be a JSON array of non-empty strings")
    if not job_ids:
        return

    database = Path(db_path)
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        for job_id in job_ids:
            mark_status(connection, source, job_id, "NONRELEVANT")
        connection.commit()


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
