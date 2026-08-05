from pathlib import Path
import sqlite3
import sys


ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "Driver"))

from collector.linkedin_preview_filter import blocked_terms
from collector.linkedin_preview_filter import load_config
from collector.linkedin_preview_filter import matches_term


DB_PATH = ROOT / "Data" / "jobs.sqlite"


def main() -> None:
    terms = blocked_terms(load_config())
    removed = 0

    with sqlite3.connect(DB_PATH) as database:
        database.execute("PRAGMA foreign_keys = ON")
        jobs = database.execute(
            "SELECT id, source_job_ref, title FROM jobs"
        ).fetchall()

        for job_id, source_job_ref, title in jobs:
            if not any(matches_term(title, term) for term in terms):
                continue

            database.execute("DELETE FROM jobs WHERE id = ?", (job_id,))
            database.execute("DELETE FROM source_jobs WHERE id = ?", (source_job_ref,))
            removed += 1
            print(f"removed: {title}")

    print(f"removed total: {removed}")


if __name__ == "__main__":
    main()
