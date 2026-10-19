"""Read stored vacancy context for the Applier without GUI data coupling."""

import json
from contextlib import closing
from pathlib import Path
import re
import sqlite3
import sys


PROJECT_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(PROJECT_ROOT / "Driver"))

from db.migrate import migrate_database


def vacancy_context(job_url: str, database: Path) -> dict:
    match = re.search(r"/jobs/view/(\d+)", job_url)
    if not match:
        return {"location": "", "easy_apply": False}
    migrate_database(database)
    with closing(sqlite3.connect(f"file:{database.as_posix()}?mode=ro", uri=True)) as connection:
        row = connection.execute(
            "SELECT coalesce(jobs.location, text.collected_location, ''), text.easy_apply "
            "FROM source_jobs "
            "LEFT JOIN jobs ON jobs.source_job_ref = source_jobs.id "
            "LEFT JOIN source_job_texts text ON text.source_job_ref = source_jobs.id "
            "WHERE source_jobs.source = ? AND source_jobs.source_job_id = ? LIMIT 1",
            ("linkedin", match.group(1)),
        ).fetchone()
    return {"location": row[0] if row else "", "easy_apply": bool(row and row[1])}


if __name__ == "__main__":
    print(json.dumps(vacancy_context(sys.argv[1], PROJECT_ROOT / "Data/jobs.sqlite")))
