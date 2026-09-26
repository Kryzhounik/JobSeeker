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

from analyzer.candidate_fit.add_fit_score import add_fit_score
from analyzer.candidate_fit.filter import filter_json_file
from analyzer.job_facts.load_input import load_input as load_job_facts_input
from analyzer.job_interest.calculate import score_json_files
from analyzer.run_logger import start_analysis_run
from codex_proxy.metrics_proxy import run
from common.paths import DATA_ROOT
from contracts.validate_json import validate_json
from db.job_registry import mark_status
from db.migrate import migrate_database
from db.save import save_records


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


def run_job_facts(
    run_id: str,
    source: str,
    job_id: str,
    target: str,
    thread_id: str | None,
    db_path: str,
) -> str:
    """Run and persist one Java-owned job-facts turn."""
    requested_thread_id = str(thread_id or "").strip() or None
    database = Path(db_path)
    schema = DRIVER_ROOT / "contracts" / "job_analysis.schema.json"
    input_value = load_job_facts_input(source, job_id, database)

    result = run(
        run_id=run_id,
        operation="job_facts",
        target=target,
        instruction_path=DRIVER_ROOT / "analyzer" / "job_facts" / "extract.md",
        input_text=_json(input_value),
        input_name=f"{source}-{job_id}.json",
        context_paths=(
            DRIVER_ROOT / "analyzer" / "job_facts" / "language_levels.md",
        ),
        output_schema=schema,
        db_path=database,
        thread_id=requested_thread_id,
    )
    if result.exit_code != 0:
        details = "; ".join(result.errors) or "Codex CLI exited unsuccessfully"
        raise RuntimeError(f"Job facts failed for {source}:{job_id}: {details}")

    analysis = json.loads(result.final_message)
    validate_json(analysis, schema)

    migrate_database(database)
    output_path = DATA_ROOT / "analyzed" / source / f"{job_id}.json"
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(analysis, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    with closing(sqlite3.connect(database)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        mark_status(connection, source, job_id, "ANALYZED")
        connection.commit()
    return result.thread_id


def start_analysis(run_id: str, vacancies_per_agent: int, db_path: str) -> None:
    start_analysis_run(run_id, 1, int(vacancies_per_agent), Path(db_path))


def run_candidate_fit(
    run_id: str,
    source: str,
    job_id: str,
    target: str,
    thread_id: str | None,
    db_path: str,
) -> str:
    """Run and persist one Java-owned candidate-fit turn."""
    requested_thread_id = str(thread_id or "").strip() or None
    database = Path(db_path)
    analyzed_path = DATA_ROOT / "analyzed" / source / f"{job_id}.json"
    scored_path = DATA_ROOT / "scored" / source / f"{job_id}.json"
    fast_result = filter_json_file(analyzed_path, scored_path)
    if not fast_result.passed:
        return requested_thread_id or ""

    schema = DRIVER_ROOT / "contracts" / "candidate_fit_result.schema.json"
    analysis = json.loads(analyzed_path.read_text(encoding="utf-8"))
    result = run(
        run_id=run_id,
        operation="candidate_fit",
        target=target,
        instruction_path=(
            DRIVER_ROOT / "analyzer" / "candidate_fit" / "evaluate.md"
        ),
        input_text=_json(analysis),
        input_name=analyzed_path.name,
        context_paths=(DRIVER_ROOT / "analyzer" / "config" / "resume.ini",),
        output_schema=schema,
        db_path=database,
        thread_id=requested_thread_id,
    )
    if result.exit_code != 0:
        details = "; ".join(result.errors) or "Codex CLI exited unsuccessfully"
        raise RuntimeError(f"Candidate fit failed for {source}:{job_id}: {details}")

    add_fit_score(
        analyzed_path,
        json.loads(result.final_message),
        scored_path,
    )
    return result.thread_id


def run_job_interest(source: str, job_id: str, db_path: str) -> None:
    scored_path = DATA_ROOT / "scored" / source / f"{job_id}.json"
    score_json_files(
        input_path=scored_path,
        config_path=(
            DRIVER_ROOT / "analyzer" / "job_interest" / "config" / "interest.ini"
        ),
        db_path=Path(db_path),
    )


def save_scored(source: str, job_ids_json: str, db_path: str) -> None:
    job_ids = json.loads(job_ids_json)
    if not isinstance(job_ids, list) or not job_ids:
        raise ValueError("job_ids must be a non-empty JSON array")
    results = save_records(
        input_value=str(DATA_ROOT / "scored" / source),
        db_path=Path(db_path),
        schema_path=DRIVER_ROOT / "db" / "schema.sql",
        source=source,
        force=False,
        job_ids=job_ids,
    )
    errors = [message for status, message in results if status == "error"]
    if errors:
        raise RuntimeError("Could not save scored jobs: " + "; ".join(errors))


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
