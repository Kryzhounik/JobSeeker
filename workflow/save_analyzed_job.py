"""Final workflow save entry after scoring is complete.

This is the single executable bridge for:
scored JSON -> SQLite save.
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


def analyzed_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.json"))


def load_record(path: Path, source: str) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    record = payload.get("analysis", payload)
    if not isinstance(record, dict):
        raise ValueError(f"{path} must contain a job analysis object")
    if source and record.get("source") != source:
        raise ValueError(
            f"{path} source mismatch: JSON has {record.get('source')!r}, "
            f"command expected {source!r}"
        )
    return record


def require_candidate_fit(record: dict[str, Any]) -> int:
    value = record.get("candidate_fit_percent")
    if value is None or value == "":
        raise ValueError(
            "candidate_fit_percent is missing; run scoring/candidate_fit before save"
        )
    return int(value)


def reject_fast_filter_only_candidate_fit(record: dict[str, Any]) -> None:
    candidate_fit = int(record.get("candidate_fit_percent") or 0)
    reason = str(record.get("candidate_fit_reason") or "").strip().lower()
    if candidate_fit <= 0:
        return
    if reason == "job filter passed":
        raise ValueError(
            "candidate_fit_percent contains only fast-filter pass result; "
            "run scoring/candidate_fit/evaluate.md semantic candidate-fit step before save"
        )


def require_job_interest(record: dict[str, Any]) -> int:
    value = record.get("job_interest")
    if value is None or value == "":
        raise ValueError("job_interest is missing; run scoring/job_interest before save")
    return int(value)


def validate_scored_record(record: dict[str, Any]) -> None:
    record["candidate_fit_percent"] = require_candidate_fit(record)
    reject_fast_filter_only_candidate_fit(record)
    record["job_interest"] = require_job_interest(record)


def save_records(
    input_path: Path,
    db_path: Path,
    schema_path: Path,
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
                source_url = str(record["source_url"] or "").strip()
                if not source_url:
                    raise ValueError("source_url is empty")
                if source_url in done and not force:
                    results.append(("skip", source_url))
                    continue

                validate_scored_record(record)
                save_job_json(connection, record)
                if source_url:
                    done.add(source_url)
                results.append(
                    (
                        "save",
                        f"{source_url or path} interest={record['job_interest']} "
                        f"fit={record['candidate_fit_percent']}",
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
        description="Save fully scored job JSON into SQLite."
    )
    parser.add_argument("--input", required=True, help="Analyzed JSON file or directory.")
    parser.add_argument("--source", default="")
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "db" / "schema.sql"))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = save_records(
        input_path=Path(args.input),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        source=args.source,
        force=args.force,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} analyzed records")


if __name__ == "__main__":
    main()
