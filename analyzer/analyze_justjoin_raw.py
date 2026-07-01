from __future__ import annotations

import argparse
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

from import_analyzed_jobs import import_records


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(
        description=(
            "Import JustJoinIT job analyses into SQLite. This script does not parse "
            "raw HTML/text; Codex produces the analysis JSON separately."
        )
    )
    parser.add_argument(
        "--input",
        default=str(ROOT / "data" / "analyzed" / "justjoin"),
        help="Analyzed JustJoinIT JSON file or directory.",
    )
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "analyzer" / "db" / "schema.sql"))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = import_records(
        input_path=Path(args.input),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        source="justjoin",
        force=args.force,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} analyzed JustJoinIT records")


if __name__ == "__main__":
    main()
