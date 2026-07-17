"""Save scored job JSON into SQLite."""

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

from common.paths import DATA_ROOT
from db.job_mapper import CANDIDATE_FIT_REASON_CODES
from db.job_mapper import existing_source_urls
from db.job_mapper import save_job_json
from db.migrate import migrate_database


def json_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.json"))


def record_from_payload(payload: Any, label: str, source: str) -> dict[str, Any]:
    record = payload.get("analysis", payload) if isinstance(payload, dict) else payload
    if not isinstance(record, dict):
        raise ValueError(f"{label} must contain a job analysis object")
    if source and record.get("source") != source:
        raise ValueError(
            f"{label} source mismatch: JSON has {record.get('source')!r}, "
            f"command expected {source!r}"
        )
    return record


def load_record(path: Path, source: str) -> dict[str, Any]:
    return record_from_payload(
        json.loads(path.read_text(encoding="utf-8")),
        path.name,
        source,
    )


def load_stdin_record(source: str) -> dict[str, Any]:
    return record_from_payload(json.load(sys.stdin), "stdin", source)


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


def require_candidate_fit_reason_code(record: dict[str, Any]) -> str:
    value = record.get("candidate_fit_reason_code")
    if value is None or value == "":
        raise ValueError(
            "candidate_fit_reason_code is missing; run "
            "scoring/candidate_fit/evaluate.md before save"
        )

    code = str(value).strip()
    if code == "undefined":
        raise ValueError(
            "candidate_fit_reason_code='undefined' is only for legacy DB rows; "
            "new scored JSON must use a real reason code"
        )
    if code not in CANDIDATE_FIT_REASON_CODES:
        raise ValueError(f"Unsupported candidate_fit_reason_code: {code!r}")
    return code


def require_candidate_fit_reason(record: dict[str, Any]) -> str:
    value = record.get("candidate_fit_reason")
    if value is None:
        raise ValueError(
            "candidate_fit_reason is missing; run scoring/candidate_fit before save"
        )
    return str(value)


def require_job_interest(record: dict[str, Any]) -> int:
    value = record.get("job_interest")
    if value is None or value == "":
        raise ValueError(
            "job_interest is missing; run scoring/job_interest/calculate.py "
            "--input <scored-json-or-dir> before save"
        )
    return int(value)


def validate_scored_record(record: dict[str, Any]) -> None:
    record["candidate_fit_percent"] = require_candidate_fit(record)
    record["candidate_fit_reason_code"] = require_candidate_fit_reason_code(record)
    record["candidate_fit_reason"] = require_candidate_fit_reason(record)
    reject_fast_filter_only_candidate_fit(record)
    record["job_interest"] = require_job_interest(record)


def save_one_record(
    connection: sqlite3.Connection,
    record: dict[str, Any],
    done: set[str],
    force: bool,
) -> tuple[str, str]:
    source_url = str(record["source_url"] or "").strip()
    if not source_url:
        raise ValueError("source_url is empty")
    if source_url in done and not force:
        return ("skip", source_url)

    validate_scored_record(record)
    save_job_json(connection, record)
    done.add(source_url)
    return (
        "save",
        f"{source_url} interest={record['job_interest']} "
        f"fit={record['candidate_fit_percent']}",
    )


def save_records(
    input_value: str,
    db_path: Path,
    schema_path: Path,
    source: str,
    force: bool,
) -> list[tuple[str, str]]:
    results: list[tuple[str, str]] = []

    migrate_database(db_path=db_path, schema_path=schema_path)

    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        done = existing_source_urls(connection)

        if input_value == "-":
            try:
                result = save_one_record(
                    connection,
                    load_stdin_record(source),
                    done,
                    force,
                )
                results.append(result)
            except Exception as error:
                results.append(("error", f"stdin :: {error}"))
        else:
            for path in json_paths(Path(input_value)):
                try:
                    result = save_one_record(
                        connection,
                        load_record(path, source),
                        done,
                        force,
                    )
                    results.append(result)
                except Exception as error:
                    results.append(("error", f"{path.name} :: {error}"))

        connection.commit()

    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="Save scored job JSON into SQLite."
    )
    parser.add_argument("--input", "-i", required=True, help="JSON file, directory, or stdin.")
    parser.add_argument("--source", default="")
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "db" / "schema.sql"))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = save_records(
        input_value=args.input,
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        source=args.source,
        force=args.force,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} scored records")
    if any(status == "error" for status, _ in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
