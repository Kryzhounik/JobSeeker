from __future__ import annotations

import sqlite3
import sys
from contextlib import closing
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest


ROOT = Path(__file__).resolve().parents[2]
DRIVER_ROOT = ROOT / "Driver"
for path in (ROOT, DRIVER_ROOT):
    if str(path) not in sys.path:
        sys.path.insert(0, str(path))

from db.job_mapper import delete_jobs
from db.job_mapper import delete_source_jobs
from db.migrate import migrate_database
from Tools.filter_database import collect_rejected_jobs


class FilterDatabaseTest(unittest.TestCase):
    def test_collect_returns_rejected_jobs_without_deleting_them(self) -> None:
        with TemporaryDirectory() as temp_directory:
            db_path = Path(temp_directory) / "jobs.sqlite"
            migrate_database(db_path)
            with closing(sqlite3.connect(db_path)) as connection:
                connection.executescript(
                    """
                    INSERT INTO source_jobs (
                        id,
                        source,
                        source_job_id,
                        processing_status
                    ) VALUES
                        (10, 'linkedin', '10', 'SAVED'),
                        (20, 'linkedin', '20', 'SAVED'),
                        (30, 'linkedin', '30', 'SAVED'),
                        (40, 'linkedin', '40', 'SAVED'),
                        (50, 'linkedin', '50', 'CLEANED');
                    INSERT INTO companies (id, name, blacklisted) VALUES
                        (100, 'Ordinary Corp', 0),
                        (200, 'Blocked Corp', 1);
                    INSERT INTO jobs (
                        id,
                        source_job_ref,
                        candidate_fit_percent
                    ) VALUES
                        (1, 10, 91),
                        (2, 20, 72),
                        (3, 30, 43),
                        (4, 40, 62);
                    INSERT INTO source_job_texts (
                        source_job_ref,
                        readable_text,
                        source_url,
                        title,
                        company_id
                    )
                    VALUES
                        (
                            10,
                            'Deep expertise in Camunda 8 is required',
                            'https://example/1',
                            'Senior Java Developer',
                            100
                        ),
                        (
                            20,
                            'Deep expertise in Camunda 8 is required',
                            'https://example/2',
                            'Backend Engineer',
                            100
                        ),
                        (
                            30,
                            'Python development',
                            'https://example/3',
                            'Senior Python Developer',
                            100
                        ),
                        (
                            40,
                            'Java development',
                            'https://example/4',
                            'Senior Java Backend Developer',
                            200
                        ),
                        (
                            50,
                            'Python development',
                            'https://example/5',
                            'Senior Python Developer',
                            100
                        );
                    """
                )
                connection.commit()

            rejected = collect_rejected_jobs(db_path)

            self.assertEqual(
                rejected,
                [
                    {
                        "source_job_ref": 20,
                        "source": "linkedin",
                        "source_job_id": "20",
                        "scope": "Job",
                        "stage": "SAVED",
                        "id": 2,
                        "title": "Backend Engineer",
                        "fit": 72,
                        "source_url": "https://example/2",
                        "original": "Deep expertise in Camunda 8 is required",
                        "matched": "Camunda",
                        "rule": "hard_blocked_technology",
                        "reason": "hard requirement for blocked technology: Camunda",
                    },
                    {
                        "source_job_ref": 30,
                        "source": "linkedin",
                        "source_job_id": "30",
                        "scope": "Job",
                        "stage": "SAVED",
                        "id": 3,
                        "title": "Senior Python Developer",
                        "fit": 43,
                        "source_url": "https://example/3",
                        "original": "Senior Python Developer",
                        "matched": "Python",
                        "rule": "title_blocked",
                        "reason": "title blocked: Python",
                    },
                    {
                        "source_job_ref": 40,
                        "source": "linkedin",
                        "source_job_id": "40",
                        "scope": "Job",
                        "stage": "SAVED",
                        "id": 4,
                        "title": "Senior Java Backend Developer",
                        "fit": 62,
                        "source_url": "https://example/4",
                        "original": "Blocked Corp",
                        "matched": "Blocked Corp",
                        "rule": "company_blacklisted",
                        "reason": "company blacklisted: Blocked Corp",
                    },
                    {
                        "source_job_ref": 50,
                        "source": "linkedin",
                        "source_job_id": "50",
                        "scope": "Collected",
                        "stage": "CLEANED",
                        "id": None,
                        "title": "Senior Python Developer",
                        "fit": None,
                        "source_url": "https://example/5",
                        "original": "Senior Python Developer",
                        "matched": "Python",
                        "rule": "title_blocked",
                        "reason": "title blocked: Python",
                    },
                ],
            )
            with closing(sqlite3.connect(db_path)) as connection:
                self.assertEqual(
                    connection.execute("SELECT count(*) FROM jobs").fetchone()[0],
                    4,
                )

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

    def test_delete_source_jobs_removes_analyzed_and_collected_rows(self) -> None:
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
            INSERT INTO source_jobs (id) VALUES (10), (20), (30);
            INSERT INTO jobs (id, source_job_ref) VALUES (1, 10);
            INSERT INTO job_details (job_id) VALUES (1);
            INSERT INTO source_job_texts (source_job_ref) VALUES (10), (20), (30);
            """
        )
        self.addCleanup(connection.close)

        deleted_refs = delete_source_jobs(connection, [10, 20, 999, 10])
        connection.commit()

        self.assertEqual(deleted_refs, [10, 20])
        self.assertEqual(connection.execute("SELECT id FROM jobs").fetchall(), [])
        self.assertEqual(connection.execute("SELECT job_id FROM job_details").fetchall(), [])
        self.assertEqual(
            connection.execute("SELECT id FROM source_jobs").fetchall(),
            [(30,)],
        )
        self.assertEqual(
            connection.execute("SELECT source_job_ref FROM source_job_texts").fetchall(),
            [(30,)],
        )


if __name__ == "__main__":
    unittest.main()
