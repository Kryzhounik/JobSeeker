"""Thin JPy adapter for the Python processing API."""

from __future__ import annotations

import json
from pathlib import Path
import sys

DRIVER_ROOT = Path(__file__).resolve().parent.parent
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from orchestrator import processing


def run_agent_filter(run_id: str, scope_json: str, db_path: str) -> str:
    return processing.run_agent_filter(run_id, json.loads(scope_json), Path(db_path))


def load_collected_scope(run_id: str, db_path: str) -> str:
    return _json(processing.load_collected_scope(run_id, Path(db_path)))


def update_batch(run_id: str, status: str, stage: str, message: str, db_path: str) -> None:
    processing.update_batch(run_id, status, stage, message, Path(db_path))


def load_statuses(scope_json: str, db_path: str) -> str:
    return _json(processing.load_statuses(json.loads(scope_json), Path(db_path)))


def run_job_facts(
    run_id: str, source: str, job_id: str, target: str,
    thread_id: str | None, db_path: str,
) -> str:
    return processing.run_job_facts(run_id, source, job_id, target, thread_id, Path(db_path))


def start_analysis(run_id: str, parallel_agents: int, vacancies_per_agent: int, db_path: str) -> None:
    processing.start_analysis(run_id, int(parallel_agents), int(vacancies_per_agent), Path(db_path))


def run_candidate_fit(
    run_id: str, source: str, job_id: str, target: str,
    thread_id: str | None, db_path: str,
) -> str:
    return processing.run_candidate_fit(run_id, source, job_id, target, thread_id, Path(db_path))


def run_job_interest(source: str, job_id: str, db_path: str) -> None:
    processing.run_job_interest(source, job_id, Path(db_path))


def save_scored(scope_json: str, db_path: str) -> int:
    return processing.save_scored(json.loads(scope_json), Path(db_path))


def mark_relevance(
    db_path: str, scope_json: str, nonrelevant_jobs_json: str,
) -> None:
    processing.mark_relevance(
        Path(db_path), json.loads(scope_json), json.loads(nonrelevant_jobs_json),
    )


def _json(value) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))
