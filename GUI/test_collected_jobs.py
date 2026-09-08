from __future__ import annotations

import sqlite3
import unittest

from GUI import jobs_viewer


class CollectedJobsTest(unittest.TestCase):
    def test_loads_collector_metadata_without_requiring_jobs_row(self) -> None:
        connection = sqlite3.connect(":memory:")
        self.addCleanup(connection.close)
        connection.row_factory = sqlite3.Row
        connection.executescript(
            """
            CREATE TABLE companies (
                id INTEGER PRIMARY KEY,
                name TEXT NOT NULL
            );
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY,
                source TEXT NOT NULL,
                source_job_id TEXT NOT NULL,
                processing_status TEXT NOT NULL,
                collection_method TEXT NOT NULL
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER PRIMARY KEY,
                readable_text TEXT NOT NULL,
                source_url TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                company_id INTEGER,
                collected_location TEXT NOT NULL DEFAULT '',
                collected_workplace TEXT NOT NULL DEFAULT 'unknown',
                collected_salary TEXT NOT NULL DEFAULT ''
            );

            INSERT INTO companies (id, name) VALUES (1, 'Example Company');
            INSERT INTO source_jobs VALUES
                (1, 'linkedin', '100', 'CLEANED', 'playwright'),
                (2, 'linkedin', '99', 'SAVED', 'browser');
            INSERT INTO source_job_texts VALUES
                (
                    1,
                    'Full collected text',
                    'https://www.linkedin.com/jobs/view/100/',
                    'Java Developer',
                    1,
                    'Moldova',
                    'remote',
                    '$100k'
                ),
                (2, 'Legacy text', '', '', NULL, '', 'unknown', '');
            """
        )

        rows = jobs_viewer.load_collected_jobs(connection)

        self.assertEqual(len(rows), 1)
        self.assertEqual(rows[0]["source_job_id"], "100")
        self.assertEqual(rows[0]["processing_status"], "CLEANED")
        self.assertEqual(rows[0]["company"], "Example Company")
        self.assertEqual(rows[0]["collected_workplace"], "remote")
        self.assertEqual(rows[0]["readable_text"], "Full collected text")


if __name__ == "__main__":
    unittest.main()
