from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.experimental_analyzer_fit import save_experimental_fit


class ExperimentalAnalyzerFitTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        self.connection.executescript(
            """
            PRAGMA foreign_keys = ON;
            CREATE TABLE job_sources (code TEXT PRIMARY KEY);
            INSERT INTO job_sources VALUES ('linkedin');
            CREATE TABLE processing_statuses (code TEXT PRIMARY KEY);
            INSERT INTO processing_statuses VALUES ('ANALYZED');
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY,
                source TEXT NOT NULL REFERENCES job_sources(code),
                source_job_id TEXT NOT NULL,
                processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
                UNIQUE(source, source_job_id)
            );
            INSERT INTO source_jobs VALUES (7, 'linkedin', '123', 'ANALYZED');
            CREATE TABLE experimental_analyzer_fits (
                source_job_ref INTEGER PRIMARY KEY REFERENCES source_jobs(id),
                analyzer_fit_percent INTEGER NOT NULL CHECK (
                    analyzer_fit_percent BETWEEN 0 AND 100
                )
            );
            """
        )

    def tearDown(self) -> None:
        self.connection.close()

    def test_fit_is_saved_and_updated_by_source_identity(self) -> None:
        source_job_ref = save_experimental_fit(
            self.connection, "linkedin", "123", 37
        )
        save_experimental_fit(self.connection, "linkedin", "123", 42)

        row = self.connection.execute(
            "SELECT source_job_ref, analyzer_fit_percent "
            "FROM experimental_analyzer_fits"
        ).fetchone()
        self.assertEqual(source_job_ref, 7)
        self.assertEqual(row, (7, 42))

    def test_unknown_source_job_is_rejected(self) -> None:
        with self.assertRaises(KeyError):
            save_experimental_fit(self.connection, "linkedin", "999", 50)


if __name__ == "__main__":
    unittest.main()
