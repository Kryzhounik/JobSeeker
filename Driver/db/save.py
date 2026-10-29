"""Save scored job JSON into SQLite."""

from __future__ import annotations

import argparse
from contextlib import closing
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.job_mapper import save_job_json
from db.job_registry import validate_identity
from db.migrate import migrate_database


def json_paths(input_path: Path, job_ids: list[str] | None = None) -> list[Path]:
    if input_path.is_file():
        if job_ids:
            raise ValueError("--job-ids requires a scored JSON directory")
        return [input_path]
    if job_ids:
        paths = [input_path / f"{job_id}.json" for job_id in job_ids]
        missing = [path.name for path in paths if not path.is_file()]
        if missing:
            raise ValueError(f"missing scored JSON files: {', '.join(missing)}")
        return paths
    return sorted(input_path.glob("*.json"))


def record_from_payload(payload: Any, label: str, source: str) -> dict[str, Any]:
    record = payload.get("analysis", payload) if isinstance(payload, dict) else payload
    if not isinstance(record, dict):
        raise ValueError(f"{label} must contain a job analysis object")
    if source and record.get("source") and record["source"] != source:
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
            "candidate_fit_percent is missing; run analyzer/candidate_fit before save"
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
            "run analyzer/candidate_fit/evaluate.md semantic candidate-fit step before save"
        )


def require_candidate_fit_reason_code(record: dict[str, Any]) -> str:
    value = record.get("candidate_fit_reason_code")
    if value is None or value == "":
        raise ValueError(
            "candidate_fit_reason_code is missing; run "
            "analyzer/candidate_fit/evaluate.md before save"
        )

    code = str(value).strip()
    if code == "undefined":
        raise ValueError(
            "candidate_fit_reason_code='undefined' is only for legacy DB rows; "
            "new scored JSON must use a real reason code"
        )
    return code


def require_candidate_fit_reason(record: dict[str, Any]) -> str:
    value = record.get("candidate_fit_reason")
    if value is None:
        raise ValueError(
            "candidate_fit_reason is missing; run analyzer/candidate_fit before save"
        )
    return str(value)


def require_job_interest(record: dict[str, Any]) -> int:
    value = record.get("job_interest")
    if value is None or value == "":
        raise ValueError(
            "job_interest is missing; run analyzer/job_interest/calculate.py "
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
    done: set[tuple[str, str]],
    force: bool,
    *,
    source: str,
    job_id: str,
) -> tuple[str, str]:
    connection.execute("SAVEPOINT save_one_record")
    try:
        identity = validate_identity(source, job_id)
        label = f"{identity[0]}:{identity[1]}"
        if identity in done and not force:
            result = ("skip", label)
        else:
            validate_scored_record(record)
            save_job_json(
                connection, record, source=identity[0], source_job_id=identity[1]
            )
            done.add(identity)
            result = (
                "save",
                f"{label} interest={record['job_interest']} "
                f"fit={record['candidate_fit_percent']}",
            )
    except Exception:
        connection.execute("ROLLBACK TO save_one_record")
        connection.execute("RELEASE save_one_record")
        raise
    connection.execute("RELEASE save_one_record")
    return result


def save_records(
    input_value: str,
    db_path: Path,
    schema_path: Path,
    source: str,
    force: bool,
    job_ids: list[str] | None = None,
) -> list[tuple[str, str]]:
    results: list[tuple[str, str]] = []

    migrate_database(db_path=db_path, schema_path=schema_path)

    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        done = set(connection.execute(
            "SELECT source_job.source, source_job.source_job_id "
            "FROM jobs job JOIN source_jobs source_job ON source_job.id = job.source_job_ref"
        ))

        if input_value == "-":
            try:
                if not job_ids or len(job_ids) != 1:
                    raise ValueError("stdin requires exactly one --job-ids value")
                record = load_stdin_record(source)
                record_source = source or str(record.get("source") or "")
                result = save_one_record(
                    connection,
                    record,
                    done,
                    force,
                    source=record_source,
                    job_id=job_ids[0],
                )
                results.append(result)
            except Exception as error:
                results.append(("error", f"stdin :: {error}"))
        else:
            for path in json_paths(Path(input_value), job_ids):
                try:
                    record = load_record(path, source)
                    result = save_one_record(
                        connection,
                        record,
                        done,
                        force,
                        source=source or str(record.get("source") or path.parent.name),
                        job_id=path.stem,
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
    parser.add_argument(
        "--job-ids",
        help="Source-job IDs to select from a directory, or one ID for stdin.",
    )
    args = parser.parse_args()

    job_ids = None
    if args.job_ids:
        job_ids = [value.strip() for value in args.job_ids.split(",") if value.strip()]
        if len(job_ids) != len(set(job_ids)):
            parser.error("--job-ids contains duplicate values")

    results = save_records(
        input_value=args.input,
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        source=args.source,
        force=args.force,
        job_ids=job_ids,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} scored records")
    if any(status == "error" for status, _ in results):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
