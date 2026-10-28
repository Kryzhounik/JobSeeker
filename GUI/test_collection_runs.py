"""Read-only Runs query against an in-memory database."""

from contextlib import nullcontext
import json
import sqlite3
import unittest
from unittest.mock import patch

from GUI import client_data


class CollectionRunsTest(unittest.TestCase):
    def test_runs_are_complete_newest_first_and_not_modified(self):
        with sqlite3.connect(":memory:") as connection:
            connection.row_factory = sqlite3.Row
            connection.execute("""
                CREATE TABLE linkedin_collection_runs (
                    run_id TEXT PRIMARY KEY, started_at TEXT, finished_at TEXT,
                    status TEXT, stage TEXT, collection_status TEXT,
                    collection_finished_at TEXT, accepted_count INTEGER,
                    stop_reason TEXT, message TEXT
                )
            """)
            connection.executemany(
                "INSERT INTO linkedin_collection_runs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                [
                    ("old", "2026-10-19 10:00:00", "2026-10-19 11:00:00", "complete",
                     "Candidate fit", "complete", "2026-10-19 10:30:00", 100, "limit_reached", ""),
                    ("new", "2026-10-28 10:00:00", None, "running", "Job facts", "complete",
                     "2026-10-28 10:30:00", 50, "", "Error details\nURL: https://example.com/job"),
                ],
            )
            before = connection.total_changes
            with patch.object(client_data, "_connection", return_value=nullcontext(connection)) as connect:
                rows = json.loads(client_data.load_collection_runs("unused.sqlite"))
            connect.assert_called_once_with("unused.sqlite", readonly=True)
            self.assertEqual(["new", "old"], [row["run_id"] for row in rows])
            self.assertIsNone(rows[0]["finished_at"])
            self.assertEqual("running", rows[0]["status"])
            self.assertEqual("Job facts", rows[0]["stage"])
            self.assertEqual(50, rows[0]["accepted_count"])
            self.assertIn("\nURL:", rows[0]["message"])
            self.assertEqual(before, connection.total_changes)
            connection.execute("DELETE FROM linkedin_collection_runs")
            with patch.object(client_data, "_connection", return_value=nullcontext(connection)):
                self.assertEqual([], json.loads(client_data.load_collection_runs("unused.sqlite")))


if __name__ == "__main__":
    unittest.main()
