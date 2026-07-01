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

from write_job import apply_schema, write_job
from common.db_import import analyzed_urls


def load_record(path: Path, source: str = "") -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    record = payload.get("analysis", payload)
    if not isinstance(record, dict):
        raise ValueError(f"{path} must contain a job analysis object")
    if source and not record.get("source"):
        record["source"] = source
    return record


def record_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.json"))


def import_records(
    input_path: Path,
    db_path: Path,
    schema_path: Path,
    source: str,
    force: bool,
) -> list[tuple[str, str]]:
    results: list[tuple[str, str]] = []
    paths = record_paths(input_path)

    with sqlite3.connect(db_path) as connection:
        apply_schema(connection, schema_path)
        done = analyzed_urls(connection)

        for path in paths:
            try:
                record = load_record(path, source)
                source_url = str(record.get("source_url") or "").strip()
                if source_url in done and not force:
                    results.append(("skip", source_url))
                    continue
                write_job(connection, record)
                if source_url:
                    done.add(source_url)
                results.append(("import", source_url or str(path)))
            except Exception as error:
                results.append(("error", f"{path.name} :: {error}"))

        connection.commit()

    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description="Import Codex-analyzed job JSON into SQLite."
    )
    parser.add_argument("--input", required=True, help="Analyzed JSON file or directory.")
    parser.add_argument("--source", default="")
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "analyzer" / "db" / "schema.sql"))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = import_records(
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
