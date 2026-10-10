"""Read a vacancy's collected location for the Applier without GUI data coupling."""

from pathlib import Path
import re
import sqlite3
import sys


def main() -> None:
    match = re.search(r"/jobs/view/(\d+)", sys.argv[1])
    if not match:
        print("")
        return

    database = Path(__file__).resolve().parent.parent / "Data/jobs.sqlite"
    with sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True) as connection:
        row = connection.execute(
            "SELECT jobs.location FROM jobs "
            "JOIN source_jobs ON jobs.source_job_ref = source_jobs.id "
            "WHERE source_jobs.source = ? AND source_jobs.source_job_id = ? LIMIT 1",
            ("linkedin", match.group(1)),
        ).fetchone()
    print(row[0] if row and row[0] else "")


if __name__ == "__main__":
    main()
