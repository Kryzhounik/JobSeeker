"""Load analyzer input and deterministic language facts in-process."""

from __future__ import annotations

import argparse
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[2]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.language_requirements import extract_language_requirements
from common.paths import DATA_ROOT
from db.migrate import migrate_database
from db.readable_text import load_readable_text


LANGUAGE_CONFIG = (
    DRIVER_ROOT / "collector" / "filtering" / "linkedin_language_filter.ini"
)


def load_input(
    source: str,
    job_id: str,
    db_path: Path = DATA_ROOT / "jobs.sqlite",
) -> dict[str, object]:
    database = Path(db_path)
    migrate_database(database)
    with closing(sqlite3.connect(database)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        readable = load_readable_text(connection, source, job_id)
    language_facts = [
        requirement.as_filter_row()
        for requirement in extract_language_requirements(readable, LANGUAGE_CONFIG)
    ]
    return {
        "source": source,
        "job_id": job_id,
        "readable_text": readable,
        "authoritative_language_facts": language_facts,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-id", required=True)
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    result = load_input(args.source, args.job_id)
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
