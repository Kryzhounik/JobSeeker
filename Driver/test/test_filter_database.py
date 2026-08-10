from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[2]
DRIVER_ROOT = ROOT / "Driver"
for path in (ROOT, DRIVER_ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from db.job_mapper import delete_jobs
from Tools.filter_database import collect_rejected_jobs


class FilterDatabaseTest(unittest.TestCase):
    def test_collect_returns_rejected_jobs_without_deleting_them(self) -> None:
        with TemporaryDirectory() as temp_directory:
            db_path = Path(temp_directory) / "jobs.sqlite"
            connection = sqlite3.connect(db_path)
            try:
                connection.executescript(
                    """
                    CREATE TABLE source_jobs (
                        id INTEGER PRIMARY KEY
                    );
                    CREATE TABLE jobs (
                        id INTEGER PRIMARY KEY,
                        source_job_ref INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        source_url TEXT NOT NULL
                    );
                    CREATE TABLE source_job_texts (
                        source_job_ref INTEGER PRIMARY KEY,
                        readable_text TEXT NOT NULL
                    );
                    INSERT INTO source_jobs (id) VALUES (10), (20), (30);
                    INSERT INTO jobs (id, source_job_ref, title, source_url) VALUES
                        (1, 10, 'Senior Java Developer', 'https://example/1'),
                        (2, 20, 'Backend Engineer', 'https://example/2'),
                        (3, 30, 'Senior Python Developer', 'https://example/3');
                    INSERT INTO source_job_texts (source_job_ref, readable_text)
                    VALUES
                        (10, 'Deep expertise in Camunda 8 is required'),
                        (20, 'Deep expertise in Camunda 8 is required');
                    """
                )
                connection.commit()
            finally:
                connection.close()

            rejected = collect_rejected_jobs(db_path)

            self.assertEqual(
                rejected,
                [
                    {
                        "id": 2,
                        "title": "Backend Engineer",
                        "source_url": "https://example/2",
                        "original": "Deep expertise in Camunda 8 is required",
                        "matched": "Camunda",
                        "rule": "hard_blocked_technology",
                        "reason": "hard requirement for blocked technology: Camunda",
                    },
                    {
                        "id": 3,
                        "title": "Senior Python Developer",
                        "source_url": "https://example/3",
                        "original": "Senior Python Developer",
                        "matched": "Python",
                        "rule": "title_blocked",
                        "reason": "title blocked: Python",
                    },
                ],
            )
            connection = sqlite3.connect(db_path)
            try:
                self.assertEqual(
                    connection.execute("SELECT count(*) FROM jobs").fetchone()[0],
                    3,
                )
            finally:
                connection.close()

    def test_delete_jobs_removes_job_source_and_cascaded_rows(self) -> None:
        connection = sqlite3.connect(":memory:")
        connection.execute("PRAGMA foreign_keys = ON")
        connection.executescript(
            """
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY
            );
            CREATE TABLE jobs (
                id INTEGER PRIMARY KEY,
                source_job_ref INTEGER NOT NULL UNIQUE
                    REFERENCES source_jobs(id)
            );
            CREATE TABLE job_details (
                job_id INTEGER PRIMARY KEY REFERENCES jobs(id) ON DELETE CASCADE
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER PRIMARY KEY
                    REFERENCES source_jobs(id) ON DELETE CASCADE
            );
            INSERT INTO source_jobs (id) VALUES (10), (20);
            INSERT INTO jobs (id, source_job_ref) VALUES (1, 10), (2, 20);
            INSERT INTO job_details (job_id) VALUES (1), (2);
            INSERT INTO source_job_texts (source_job_ref) VALUES (10), (20);
            """
        )
        self.addCleanup(connection.close)

        deleted_ids = delete_jobs(connection, [1, 999, 1])
        connection.commit()

        self.assertEqual(deleted_ids, [1])
        self.assertEqual(connection.execute("SELECT id FROM jobs").fetchall(), [(2,)])
        self.assertEqual(
            connection.execute("SELECT id FROM source_jobs").fetchall(),
            [(20,)],
        )
        self.assertEqual(
            connection.execute("SELECT job_id FROM job_details").fetchall(),
            [(2,)],
        )
        self.assertEqual(
            connection.execute("SELECT source_job_ref FROM source_job_texts").fetchall(),
            [(20,)],
        )


if __name__ == "__main__":
    unittest.main()
