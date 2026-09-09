from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.job_registry import is_registered
from db.job_registry import mark_status
from db.job_registry import source_job_id


class JobRegistryTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        self.connection.executescript(
            """
            PRAGMA foreign_keys = ON;
            CREATE TABLE job_sources (code TEXT PRIMARY KEY);
            INSERT INTO job_sources VALUES ('linkedin'), ('justjoin');
            CREATE TABLE processing_statuses (
                code TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL UNIQUE
            );
            INSERT INTO processing_statuses VALUES
                ('RAW', 10), ('CLEANED', 20), ('NONRELEVANT', 25),
                ('ANALYZED', 30),
                ('SCORED', 40), ('SAVED', 50);
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY,
                source TEXT NOT NULL REFERENCES job_sources(code),
                source_job_id TEXT NOT NULL,
                processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
                collection_method TEXT NOT NULL DEFAULT 'unknown' CHECK (
                    collection_method IN ('unknown', 'script', 'browser', 'playwright')
                ),
                UNIQUE(source, source_job_id)
            );
            """
        )

    def tearDown(self) -> None:
        self.connection.close()

    def test_status_only_moves_forward(self) -> None:
        mark_status(self.connection, "linkedin", "123", "RAW")
        mark_status(self.connection, "linkedin", "123", "CLEANED")
        mark_status(self.connection, "linkedin", "123", "RAW")

        status = self.connection.execute(
            "SELECT processing_status FROM source_jobs"
        ).fetchone()[0]
        self.assertEqual(status, "CLEANED")
        self.assertTrue(is_registered(self.connection, "linkedin", "123"))

    def test_collection_method_records_first_collector(self) -> None:
        mark_status(
            self.connection,
            "linkedin",
            "123",
            "RAW",
            collection_method="script",
        )
        mark_status(
            self.connection,
            "linkedin",
            "123",
            "CLEANED",
            collection_method="browser",
        )

        method = self.connection.execute(
            "SELECT collection_method FROM source_jobs"
        ).fetchone()[0]
        self.assertEqual(method, "script")

    def test_marks_cleaned_job_nonrelevant(self) -> None:
        mark_status(self.connection, "linkedin", "123", "CLEANED")
        mark_status(self.connection, "linkedin", "123", "NONRELEVANT")

        status = self.connection.execute(
            "SELECT processing_status FROM source_jobs"
        ).fetchone()[0]
        self.assertEqual(status, "NONRELEVANT")

    def test_collection_method_replaces_legacy_unknown(self) -> None:
        mark_status(self.connection, "linkedin", "123", "RAW")
        mark_status(
            self.connection,
            "linkedin",
            "123",
            "RAW",
            collection_method="browser",
        )

        method = self.connection.execute(
            "SELECT collection_method FROM source_jobs"
        ).fetchone()[0]
        self.assertEqual(method, "browser")

    def test_source_job_id_uses_canonical_url_identity(self) -> None:
        self.assertEqual(
            source_job_id("linkedin", "https://www.linkedin.com/jobs/view/4439052436/"),
            "4439052436",
        )
        self.assertEqual(
            source_job_id("justjoin", "https://justjoin.it/job-offer/example-slug"),
            "example-slug",
        )


if __name__ == "__main__":
    unittest.main()
