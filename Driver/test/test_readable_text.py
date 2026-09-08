from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.readable_text import has_readable_text
from db.readable_text import load_readable_text
from db.readable_text import save_collected_job
from db.readable_text import save_readable_text


class ReadableTextTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        self.connection.execute("PRAGMA foreign_keys = ON")
        self.connection.executescript(
            """
            CREATE TABLE job_sources (code TEXT PRIMARY KEY);
            INSERT INTO job_sources (code) VALUES ('linkedin');
            CREATE TABLE processing_statuses (
                code TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL UNIQUE
            );
            INSERT INTO processing_statuses (code, sort_order) VALUES
                ('RAW', 10), ('CLEANED', 20), ('ANALYZED', 30),
                ('SCORED', 40), ('SAVED', 50);
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL REFERENCES job_sources(code),
                source_job_id TEXT NOT NULL,
                processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
                collection_method TEXT NOT NULL DEFAULT 'unknown' CHECK (
                    collection_method IN ('unknown', 'script', 'browser', 'playwright')
                ),
                UNIQUE(source, source_job_id)
            );
            CREATE TABLE companies (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER PRIMARY KEY
                    REFERENCES source_jobs(id) ON DELETE CASCADE,
                readable_text TEXT NOT NULL CHECK (length(trim(readable_text)) > 0),
                source_url TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                company_id INTEGER REFERENCES companies(id) ON DELETE SET NULL,
                collected_location TEXT NOT NULL DEFAULT '',
                collected_workplace TEXT NOT NULL DEFAULT 'unknown' CHECK (
                    collected_workplace IN ('remote', 'hybrid', 'office', 'unknown')
                ),
                collected_salary TEXT NOT NULL DEFAULT ''
            );
            """
        )

    def tearDown(self) -> None:
        self.connection.close()

    def test_saves_loads_and_replaces_one_text_per_source_job(self) -> None:
        save_readable_text(self.connection, "linkedin", "123", "first")
        save_readable_text(self.connection, "linkedin", "123", "second")

        self.assertTrue(has_readable_text(self.connection, "linkedin", "123"))
        self.assertEqual(load_readable_text(self.connection, "linkedin", "123"), "second")
        self.assertEqual(
            self.connection.execute("SELECT count(*) FROM source_job_texts").fetchone()[0],
            1,
        )
        self.assertEqual(
            self.connection.execute(
                "SELECT processing_status FROM source_jobs"
            ).fetchone()[0],
            "CLEANED",
        )

    def test_rejects_empty_text(self) -> None:
        with self.assertRaises(ValueError):
            save_readable_text(self.connection, "linkedin", "123", "  ")

    def test_saves_normalized_collector_metadata(self) -> None:
        save_collected_job(
            self.connection,
            "linkedin",
            "123",
            "readable",
            source_url=" https://www.linkedin.com/jobs/view/123/ ",
            title=" Senior   Java Developer ",
            company=" Example   Company ",
            location=" EMEA   (Remote) ",
            workplace="On-site",
            salary=" $100,000   -   $120,000 ",
        )

        row = self.connection.execute(
            """
            SELECT
                text.readable_text,
                text.source_url,
                text.title,
                company.name,
                text.collected_location,
                text.collected_workplace,
                text.collected_salary
            FROM source_job_texts text
            LEFT JOIN companies company ON company.id = text.company_id
            """
        ).fetchone()
        self.assertEqual(
            row,
            (
                "readable",
                "https://www.linkedin.com/jobs/view/123/",
                "Senior Java Developer",
                "Example Company",
                "EMEA (Remote)",
                "office",
                "$100,000 - $120,000",
            ),
        )

        save_readable_text(self.connection, "linkedin", "123", "updated")
        preserved = self.connection.execute(
            """
            SELECT readable_text, title, collected_workplace
            FROM source_job_texts
            """
        ).fetchone()
        self.assertEqual(preserved, ("updated", "Senior Java Developer", "office"))

    def test_deleting_source_job_deletes_its_text(self) -> None:
        save_readable_text(self.connection, "linkedin", "123", "text")
        self.connection.execute("DELETE FROM source_jobs WHERE source_job_id = '123'")
        self.assertEqual(
            self.connection.execute("SELECT count(*) FROM source_job_texts").fetchone()[0],
            0,
        )

    def test_metadata_migration_preserves_existing_text(self) -> None:
        connection = sqlite3.connect(":memory:")
        try:
            connection.executescript(
                """
                CREATE TABLE companies (id INTEGER PRIMARY KEY);
                CREATE TABLE source_jobs (id INTEGER PRIMARY KEY);
                CREATE TABLE source_job_texts (
                    source_job_ref INTEGER PRIMARY KEY REFERENCES source_jobs(id),
                    readable_text TEXT NOT NULL
                );
                INSERT INTO source_jobs (id) VALUES (1);
                INSERT INTO source_job_texts (source_job_ref, readable_text)
                VALUES (1, 'existing text');
                """
            )
            migration = (
                DRIVER_ROOT
                / "db/migrations/022_source_job_collected_metadata.sql"
            ).read_text(encoding="utf-8")
            connection.executescript(migration)

            row = connection.execute(
                """
                SELECT
                    readable_text,
                    source_url,
                    title,
                    company_id,
                    collected_location,
                    collected_workplace,
                    collected_salary
                FROM source_job_texts
                """
            ).fetchone()
            self.assertEqual(
                row,
                ("existing text", "", "", None, "", "unknown", ""),
            )
        finally:
            connection.close()


if __name__ == "__main__":
    unittest.main()
