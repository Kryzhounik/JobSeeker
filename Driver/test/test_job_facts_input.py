from __future__ import annotations

from contextlib import closing
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.job_facts.load_input import load_input


class JobFactsInputTest(unittest.TestCase):
    def test_loads_text_and_extracts_language_facts_in_process(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            with closing(sqlite3.connect(database)) as connection:
                connection.executescript(
                    """
                    CREATE TABLE source_jobs (
                        id INTEGER PRIMARY KEY,
                        source TEXT NOT NULL,
                        source_job_id TEXT NOT NULL
                    );
                    CREATE TABLE source_job_texts (
                        source_job_ref INTEGER PRIMARY KEY,
                        readable_text TEXT NOT NULL
                    );
                    INSERT INTO source_jobs (id, source, source_job_id)
                    VALUES (1, 'linkedin', '123');
                    INSERT INTO source_job_texts (source_job_ref, readable_text)
                    VALUES (1, 'Senior Java Developer\nEnglish: C1 Advanced');
                    """
                )

            with patch(
                "analyzer.job_facts.load_input.migrate_database"
            ) as migrate:
                result = load_input("linkedin", "123", database)

            migrate.assert_called_once_with(database)
            self.assertEqual(result["source"], "linkedin")
            self.assertEqual(result["job_id"], "123")
            self.assertIn("Senior Java Developer", result["readable_text"])
            self.assertEqual(
                result["authoritative_language_facts"],
                [{
                    "name": "English",
                    "level": "C1",
                    "level_rank": 5,
                    "raw_value": "English: C1 Advanced",
                }],
            )


if __name__ == "__main__":
    unittest.main()
