"""CLI wrapper for saving analyzed job JSON to SQLite.

The canonical JSON <-> SQLite mapping lives in db/job_mapper.py. Keep this file
thin so agents do not accidentally add a second database mapping path here.
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

from db.job_mapper import apply_schema
from db.job_mapper import save_job_json
# Backward-compatible import for old callers; new code should use db.job_mapper.
from db.job_mapper import write_job


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_record(input_path: str) -> dict[str, Any]:
    if input_path == "-":
        return json.load(sys.stdin)
    with Path(input_path).open("r", encoding="utf-8") as file:
        return json.load(file)


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser(description="Write analyzed job JSON to SQLite.")
    parser.add_argument("--input", "-i", default="-")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "db" / "schema.sql"))
    args = parser.parse_args()

    record = load_record(args.input)
    with sqlite3.connect(args.db) as connection:
        apply_schema(connection, Path(args.schema))
        job_id = save_job_json(connection, record)
    print(f"wrote job_id={job_id} {record['source_url']}")


if __name__ == "__main__":
    main()
