"""Status and availability filters against an in-memory database."""

from contextlib import nullcontext
import json
import sqlite3
import unittest
from unittest.mock import patch

from GUI import client_data


class JobFiltersTest(unittest.TestCase):
    def setUp(self):
        self.connection = sqlite3.connect(":memory:")
        self.addCleanup(self.connection.close)
        self.connection.row_factory = sqlite3.Row
        self.connection.executescript("""
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY, source_job_id TEXT,
                availability TEXT, unavailable_checks INTEGER DEFAULT 0
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER, easy_apply INTEGER, company_id INTEGER
            );
            CREATE TABLE jobs (
                id INTEGER PRIMARY KEY, source_job_ref INTEGER,
                candidate_fit_reason_code TEXT, candidate_fit_reason TEXT
            );
            CREATE TABLE applications (id INTEGER PRIMARY KEY, job_id INTEGER);
            CREATE VIEW job_list AS
                SELECT j.id AS job_id, 100 AS score, 50 AS fit, 400 AS interest,
                    CASE WHEN j.id = 2 THEN 'Closed' ELSE 'New' END AS status,
                    '' AS remote_scope, '' AS relocation, '' AS location,
                    '' AS company, '' AS title, '' AS role, '' AS seniority,
                    '' AS primary_language, '' AS salary, '' AS added_at,
                    '' AS source_url
                FROM jobs j;
            INSERT INTO source_jobs (id, availability) VALUES
                (1, 'available'), (2, 'available'), (3, 'unavailable');
            INSERT INTO jobs (id, source_job_ref) VALUES (1, 1), (2, 2), (3, 3);
        """)

    def load_ids(self, statuses, show_unavailable):
        filters = {
            "statuses": statuses, "show_unavailable": show_unavailable,
            "show_zero": True,
        }
        with patch.object(client_data, "_connection",
                          return_value=nullcontext(self.connection)):
            rows = json.loads(client_data.load_jobs("unused.sqlite", json.dumps(filters)))
        return {row["job_id"] for row in rows}

    def test_new_excludes_closed_and_unavailable(self):
        self.assertEqual({1}, self.load_ids(["New"], False))

    def test_show_unavailable_does_not_add_closed_status(self):
        self.assertEqual({1, 3}, self.load_ids(["New"], True))

    def test_closed_is_returned_only_when_requested(self):
        self.assertEqual({2}, self.load_ids(["Closed"], False))

    def test_no_selected_statuses_returns_no_jobs(self):
        self.assertEqual(set(), self.load_ids([], True))


if __name__ == "__main__":
    unittest.main()
