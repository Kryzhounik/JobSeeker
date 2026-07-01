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
if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

from common.db_import import analyzed_urls
from common.job_filter import evaluate_job
from valuate_jobs import load_config
from valuate_jobs import relocation_score
from valuate_jobs import remote_score
from valuate_jobs import technology_score
from write_job import apply_schema
from write_job import write_job


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


def score_record(
    record: dict[str, Any],
    valuation_config_path: Path,
    resume_path: Path,
    filter_path: Path,
) -> str:
    filter_result = evaluate_job(
        title=str(record.get("title") or ""),
        required_languages=record.get("languages", []),
        remote_type=str(record.get("remote_type") or ""),
        remote_scope=str(record.get("remote_scope") or ""),
        relocation=str(record.get("relocation") or ""),
        resume_path=resume_path,
        filter_path=filter_path,
    )

    score = 0
    if filter_result.passed:
        config = load_config(valuation_config_path)
        score = (
            remote_score(
                config,
                str(record.get("remote_type") or ""),
                str(record.get("remote_scope") or ""),
            )
            + relocation_score(config, str(record.get("relocation") or ""))
            + technology_score(config, technology_names(record))
        )
        if score == 0:
            score = 1

    record["fitability_percent"] = filter_result.fitability_percent
    record["valuation"] = score
    return filter_result.reason


def save_records(
    input_path: Path,
    db_path: Path,
    schema_path: Path,
    valuation_config_path: Path,
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
                    valuation_config_path,
                    resume_path,
                    filter_path,
                )
                write_job(connection, record)
                if source_url:
                    done.add(source_url)
                results.append(
                    (
                        "save",
                        f"{source_url or path} valuation={record['valuation']} "
                        f"fitability={record['fitability_percent']} reason={reason}",
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
        description="Save analyzed job JSON into SQLite with filters and valuation."
    )
    parser.add_argument("--input", required=True, help="Analyzed JSON file or directory.")
    parser.add_argument("--source", default="")
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "analyzer" / "db" / "schema.sql"))
    parser.add_argument(
        "--valuation-config",
        default=str(ROOT / "analyzer" / "config" / "valuation.ini"),
    )
    parser.add_argument("--resume", default=str(ROOT / "common" / "config" / "resume.ini"))
    parser.add_argument(
        "--filter-config",
        default=str(ROOT / "common" / "config" / "filter.ini"),
    )
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = save_records(
        input_path=Path(args.input),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        valuation_config_path=Path(args.valuation_config),
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
