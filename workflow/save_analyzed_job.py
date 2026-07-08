"""Workflow entry after Codex produced analyzed JSON.

This is the single executable bridge for:
analyzed JSON -> candidate fit -> job interest -> SQLite save.
Do not add extraction/parsing logic here.
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.db_import import analyzed_urls
from db.job_mapper import apply_schema
from db.job_mapper import save_job_json
from scoring.candidate_fit.filter import evaluate_job
from scoring.job_interest.calculate import load_config
from scoring.job_interest.calculate import relocation_score
from scoring.job_interest.calculate import remote_score
from scoring.job_interest.calculate import technology_score


def analyzed_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.json"))


def load_record(path: Path, source: str) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    record = payload.get("analysis", payload)
    if not isinstance(record, dict):
        raise ValueError(f"{path} must contain a job analysis object")
    if source and not record.get("source"):
        record["source"] = source
    return record


def technology_names(record: dict[str, Any]) -> list[str]:
    result: list[str] = []
    for item in record.get("technologies", []):
        if isinstance(item, dict):
            name = str(item.get("name") or "").strip()
        else:
            name = str(item or "").strip()
        if name:
            result.append(name)
    return result


def calculate_candidate_fit(
    record: dict[str, Any],
    resume_path: Path,
    filter_path: Path,
) -> tuple[int, str]:
    result = evaluate_job(
        title=str(record.get("title") or ""),
        required_languages=record.get("languages", []),
        remote_type=str(record.get("remote_type") or ""),
        remote_scope=str(record.get("remote_scope") or ""),
        relocation=str(record.get("relocation") or ""),
        resume_path=resume_path,
        filter_path=filter_path,
    )
    return result.candidate_fit_percent, result.reason


def calculate_job_interest(
    record: dict[str, Any],
    interest_config_path: Path,
) -> int:
    config = load_config(interest_config_path)
    score = (
        remote_score(
            config,
            str(record.get("remote_type") or ""),
            str(record.get("remote_scope") or ""),
        )
        + relocation_score(config, str(record.get("relocation") or ""))
        + technology_score(config, technology_names(record))
    )
    return score


def score_record(
    record: dict[str, Any],
    interest_config_path: Path,
    resume_path: Path,
    filter_path: Path,
) -> str:
    candidate_fit, reason = calculate_candidate_fit(record, resume_path, filter_path)
    interest = calculate_job_interest(record, interest_config_path)
    if interest == 0 and candidate_fit > 0:
        score = 1
    else:
        score = interest

    record["candidate_fit_percent"] = candidate_fit
    record["job_interest"] = score
    return reason


def save_records(
    input_path: Path,
    db_path: Path,
    schema_path: Path,
    interest_config_path: Path,
    resume_path: Path,
    filter_path: Path,
    source: str,
    force: bool,
) -> list[tuple[str, str]]:
    results: list[tuple[str, str]] = []

    with sqlite3.connect(db_path) as connection:
        apply_schema(connection, schema_path)
        done = analyzed_urls(connection)

        for path in analyzed_paths(input_path):
            try:
                record = load_record(path, source)
                source_url = str(record.get("source_url") or "").strip()
                if source_url in done and not force:
                    results.append(("skip", source_url))
                    continue

                reason = score_record(
                    record,
                    interest_config_path,
                    resume_path,
                    filter_path,
                )
                save_job_json(connection, record)
                if source_url:
                    done.add(source_url)
                results.append(
                    (
                        "save",
                        f"{source_url or path} interest={record['job_interest']} "
                        f"fit={record['candidate_fit_percent']} reason={reason}",
                    )
                )
            except Exception as error:
                results.append(("error", f"{path.name} :: {error}"))

        connection.commit()

    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="Save analyzed job JSON into SQLite with fit and interest scoring."
    )
    parser.add_argument("--input", required=True, help="Analyzed JSON file or directory.")
    parser.add_argument("--source", default="")
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "db" / "schema.sql"))
    parser.add_argument(
        "--interest-config",
        default=str(ROOT / "scoring" / "job_interest" / "config" / "interest.ini"),
    )
    parser.add_argument(
        "--resume",
        default=str(ROOT / "scoring" / "candidate_fit" / "config" / "resume.ini"),
    )
    parser.add_argument(
        "--filter-config",
        default=str(ROOT / "scoring" / "candidate_fit" / "config" / "filter.ini"),
    )
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = save_records(
        input_path=Path(args.input),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        interest_config_path=Path(args.interest_config),
        resume_path=Path(args.resume),
        filter_path=Path(args.filter_config),
        source=args.source,
        force=args.force,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} analyzed records")


if __name__ == "__main__":
    main()
