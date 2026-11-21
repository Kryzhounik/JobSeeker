"""Create deterministic GUI fixtures using the application's real migrations."""

import sqlite3
import sys
from contextlib import closing
from pathlib import Path

root, database = map(Path, sys.argv[1:])
sys.path.insert(0, str(root / "Driver"))
from db.migrate import migrate_database

migrate_database(database)
with closing(sqlite3.connect(database)) as connection:
    connection.execute("PRAGMA foreign_keys = ON")
    connection.execute("INSERT INTO companies (id, name) VALUES (1, 'Fixture Company')")
    for job_id, status, fit, interest in (
        (1, "New", 60, 400), (2, "Checked", 80, 500), (3, "Applied", 90, 600)
    ):
        connection.execute(
            "INSERT INTO source_jobs (id, source, source_job_id, processing_status) "
            "VALUES (?, 'linkedin', ?, 'SAVED')", (job_id, str(1000 + job_id))
        )
        connection.execute(
            "INSERT INTO source_job_texts "
            "(source_job_ref, readable_text, source_url, title, company_id) VALUES (?, ?, ?, ?, 1)",
            (job_id, "Test vacancy description", "" if job_id == 2 else
             f"https://www.linkedin.com/jobs/view/{1000 + job_id}/", f"Java Developer {job_id}")
        )
        connection.execute(
            "INSERT INTO jobs (id, source_job_ref, status, candidate_fit_percent, job_interest, "
            "candidate_fit_reason_code, added_at) VALUES (?, ?, ?, ?, ?, 'ok', '2026-11-01')",
            (job_id, job_id, status, fit, interest)
        )
    connection.execute("INSERT INTO applications (job_id, status, applied_at) "
                       "VALUES (3, 'Applied', '2026-11-01')")
    connection.commit()
