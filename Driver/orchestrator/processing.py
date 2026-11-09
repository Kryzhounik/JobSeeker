"""Python application operations; callable independently of the JPy adapter."""

from __future__ import annotations

from contextlib import closing
import json
from pathlib import Path
import sqlite3
from typing import Any


DRIVER_ROOT = Path(__file__).resolve().parent.parent

from analyzer.candidate_fit.add_fit_score import add_fit_score
from analyzer.candidate_fit.filter import filter_json_file
from analyzer.job_facts.load_input import load_input as load_job_facts_input
from analyzer.job_interest.calculate import score_json_files
from analyzer.run_logger import start_analysis_run
from collector.filtering.language_requirements import DEFAULT_LANGUAGE_CONFIG
from codex_proxy.metrics_proxy import run
from common.paths import DATA_ROOT
from contracts.validate_json import validate_json
from db.job_registry import mark_status
from db.migrate import migrate_database
from db.save import save_records


def run_agent_filter(run_id: str, scope: list[dict[str, Any]], db_path: Path) -> str:
    """Run the existing title filter once through the metered Codex CLI."""
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
        db_path=db_path,
    )
    if result.exit_code != 0:
        details = "; ".join(result.errors) or "Codex CLI exited unsuccessfully"
        raise RuntimeError(f"Agent filter failed: {details}")
    if not result.final_message.strip():
        raise RuntimeError("Agent filter returned an empty response")
    return result.final_message


def load_collected_scope(run_id: str, db_path: Path) -> list[dict[str, str]]:
    """Return saved, processable vacancies even if collection stopped early."""
    database = db_path
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        run_row = connection.execute(
            "SELECT status FROM linkedin_collection_runs WHERE run_id = ?",
            (run_id,),
        ).fetchone()
        if run_row is None:
            raise ValueError(f"Batch not found: {run_id}")
        if run_row[0] == "running":
            raise ValueError(f"Batch is already running: {run_id}")
        if run_row[0] == "complete":
            raise ValueError(f"Batch is already complete: {run_id}")
        rows = connection.execute(
            """
            SELECT job.source_job_id, text.title
            FROM source_jobs job
            JOIN source_job_texts text ON text.source_job_ref = job.id
            WHERE job.collection_run_id = ?
              AND job.source = 'linkedin'
              AND job.processing_status IN (
                  'CLEANED', 'RELEVANT', 'NONRELEVANT', 'ANALYZED', 'SCORED', 'SAVED'
              )
            ORDER BY job.id
            """,
            (run_id,),
        ).fetchall()
    return [
        {"source": "linkedin", "job_id": job_id, "title": title}
        for job_id, title in rows
    ]


def update_batch(run_id: str, status: str, stage: str, message: str, db_path: Path) -> None:
    database = db_path
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        # A partial finish-analysis command must not complete the rest of its batch.
        if status == "complete" and connection.execute(
            "SELECT 1 FROM source_jobs WHERE collection_run_id = ? "
            "AND processing_status IN ('RAW', 'CLEANED', 'RELEVANT', 'ANALYZED', 'SCORED') LIMIT 1",
            (run_id,),
        ).fetchone():
            status = "pending"
        connection.execute(
            """
            UPDATE linkedin_collection_runs
            SET status = ?, stage = CASE WHEN ? = '' THEN stage ELSE ? END,
                finished_at = CASE WHEN ? IN ('running', 'pending') THEN NULL ELSE CURRENT_TIMESTAMP END,
                message = ?
            WHERE run_id = ?
            """,
            (status, stage, stage, status, message, run_id),
        )
        connection.commit()


def load_statuses(source: str, job_ids: list[str], db_path: Path) -> dict[str, str]:
    database = db_path
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        placeholders = ",".join("?" for _ in job_ids)
        rows = connection.execute(
            f"SELECT source_job_id, processing_status FROM source_jobs "
            f"WHERE source = ? AND source_job_id IN ({placeholders})",
            (source, *job_ids),
        ).fetchall()
    if len(rows) != len(job_ids):
        raise ValueError("Scope contains missing or duplicate source-job IDs")
    return dict(rows)


def run_job_facts(
    run_id: str,
    source: str,
    job_id: str,
    target: str,
    thread_id: str | None,
    db_path: Path,
) -> str:
    """Run and persist one Java-owned job-facts turn."""
    requested_thread_id = str(thread_id or "").strip() or None
    database = db_path
    schema = DRIVER_ROOT / "contracts" / "job_analysis.schema.json"
    input_value = load_job_facts_input(source, job_id, database)

    result = run(
        run_id=run_id,
        operation="job_facts",
        target=target,
        instruction_path=DRIVER_ROOT / "analyzer" / "job_facts" / "extract.md",
        input_text=_json({"readable_text": input_value["readable_text"]}),
        input_name=f"{source}-{job_id}.json",
        context_paths=(
            DEFAULT_LANGUAGE_CONFIG,
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
    # Keep agent extraction independent of script facts. Missing or conflicting
    # explicit CEFR must fail so parser/prompt errors remain visible; do not
    # silently fill or overwrite the agent's result.
    languages = {row["name"].strip().casefold(): row for row in analysis["languages"]}
    for fact in input_value["script_language_facts"]:
        if not fact["exact"]:
            continue
        observed = languages.get(fact["name"].casefold())
        if (
            observed is None
            or observed["level"].strip().upper() != fact["level"]
            or observed["level_rank"] != fact["level_rank"]
        ):
            actual = (
                "missing" if observed is None
                else f"{observed['level']} / rank {observed['level_rank']}"
            )
            raise ValueError(
                f"CEFR conflict for {source}:{job_id} ({fact['name']}): "
                f"script {fact['level']} / rank {fact['level_rank']}, agent {actual}"
            )

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


def start_analysis(run_id: str, parallel_agents: int, vacancies_per_agent: int, db_path: Path) -> None:
    start_analysis_run(run_id, parallel_agents, vacancies_per_agent, db_path)


def run_candidate_fit(
    run_id: str,
    source: str,
    job_id: str,
    target: str,
    thread_id: str | None,
    db_path: Path,
) -> str:
    """Run and persist one Java-owned candidate-fit turn."""
    requested_thread_id = str(thread_id or "").strip() or None
    database = db_path
    analyzed_path = DATA_ROOT / "analyzed" / source / f"{job_id}.json"
    scored_path = DATA_ROOT / "scored" / source / f"{job_id}.json"
    fast_result = filter_json_file(analyzed_path, scored_path)
    if not fast_result.passed:
        return requested_thread_id or ""

    if requested_thread_id is None:
        with closing(sqlite3.connect(database)) as connection:
            previous = connection.execute(
                """
                SELECT thread_id FROM codex_invocations
                WHERE run_id = ? AND operation = 'candidate_fit' AND target = ?
                  AND status = 'success' AND thread_id <> ''
                ORDER BY id DESC LIMIT 1
                """,
                (run_id, target),
            ).fetchone()
        if previous:
            requested_thread_id = previous[0]

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


def run_job_interest(source: str, job_id: str, db_path: Path) -> None:
    scored_path = DATA_ROOT / "scored" / source / f"{job_id}.json"
    score_json_files(
        input_path=scored_path,
        config_path=(
            DRIVER_ROOT / "analyzer" / "job_interest" / "config" / "interest.ini"
        ),
        db_path=db_path,
    )


def save_scored(source: str, job_ids: list[str], db_path: Path) -> int:
    if not isinstance(job_ids, list) or not job_ids:
        raise ValueError("job_ids must be a non-empty JSON array")
    results = save_records(
        input_value=str(DATA_ROOT / "scored" / source),
        db_path=db_path,
        schema_path=DRIVER_ROOT / "db" / "schema.sql",
        source=source,
        force=False,
        job_ids=job_ids,
    )
    errors = [message for status, message in results if status == "error"]
    if errors:
        raise RuntimeError("Could not save scored jobs: " + "; ".join(errors))
    return sum(status == "save" for status, _ in results)


def mark_relevance(
    db_path: Path, source: str, job_ids: list[str], nonrelevant_ids: list[str]
) -> None:
    """Persist both accepted and rejected filter decisions atomically."""
    rejected = set(nonrelevant_ids)
    if not isinstance(job_ids, list) or not all(
        isinstance(job_id, str) and job_id for job_id in job_ids
    ):
        raise ValueError("job_ids must be a JSON array of non-empty strings")
    if not job_ids:
        return
    if len(set(job_ids)) != len(job_ids) or not rejected.issubset(job_ids):
        raise ValueError("Invalid relevance decisions for scope")

    database = db_path
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        for job_id in job_ids:
            status = "NONRELEVANT" if job_id in rejected else "RELEVANT"
            updated = connection.execute(
                """
                UPDATE source_jobs SET processing_status = ?
                WHERE source = ? AND source_job_id = ? AND processing_status = 'CLEANED'
                """,
                (status, source, job_id),
            )
            if updated.rowcount != 1:
                raise ValueError(f"Expected CLEANED source job: {source}:{job_id}")
        connection.commit()


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
