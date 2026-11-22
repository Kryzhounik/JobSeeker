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
                availability TEXT, unavailable_checks INTEGER DEFAULT 0, semantic_duplicate_of INTEGER
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER, easy_apply INTEGER, company_id INTEGER, source_url TEXT
            );
            CREATE TABLE jobs (
                id INTEGER PRIMARY KEY, source_job_ref INTEGER,
                candidate_fit_reason_code TEXT, candidate_fit_reason TEXT
            );
            CREATE TABLE applications (id INTEGER PRIMARY KEY, job_id INTEGER);
            CREATE VIEW job_list AS
                SELECT j.id AS job_id, 100 AS score, 50 AS fit, 400 AS interest,
                    CASE WHEN j.id IN (2, 4) THEN 'Closed'
                         WHEN j.id = 5 THEN 'Checked' ELSE 'New' END AS status,
                    '' AS remote_scope, '' AS relocation, '' AS location,
                    '' AS company, '' AS title, '' AS role, '' AS seniority,
                    '' AS primary_language, '' AS salary, '' AS added_at,
                    '' AS source_url
                FROM jobs j;
            INSERT INTO source_jobs (id, availability) VALUES
                (1, 'available'), (2, 'available'), (3, 'unavailable'),
                (4, 'closed'), (5, 'unavailable'), (6, 'closed'), (7, 'deleted');
            INSERT INTO jobs (id, source_job_ref) VALUES
                (1, 1), (2, 2), (3, 3), (4, 4), (5, 5), (6, 6), (7, 7);
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

    def test_unavailable_widens_availability_within_selected_statuses(self):
        self.assertEqual({1, 3, 6, 7}, self.load_ids(["New"], True))
        self.assertEqual({5}, self.load_ids(["Checked"], True))
        self.assertEqual(set(), self.load_ids(["Checked"], False))

    def test_closed_is_returned_only_when_requested(self):
        self.assertEqual({2}, self.load_ids(["Closed"], False))
        self.assertEqual({2, 4}, self.load_ids(["Closed"], True))

    def test_no_selected_statuses_returns_no_jobs(self):
        for show_unavailable in (False, True):
            with self.subTest(show_unavailable=show_unavailable):
                self.assertEqual(set(), self.load_ids([], show_unavailable))

    def test_duplicate_parent_ignores_child_filters(self):
        self.connection.execute("UPDATE source_jobs SET semantic_duplicate_of = 2 WHERE id = 1")
        with patch.object(client_data, "_connection", return_value=nullcontext(self.connection)):
            rows = json.loads(client_data.load_jobs("unused.sqlite", json.dumps({
                "statuses": ["New"], "show_unavailable": False, "show_zero": True,
                "id_query": "1",
            })))
        self.assertEqual([1], [row["job_id"] for row in rows])
        self.assertEqual(2, rows[0]["duplicate_parent"]["job_id"])
        self.assertEqual("Closed", rows[0]["duplicate_parent"]["status"])


if __name__ == "__main__":
    unittest.main()
