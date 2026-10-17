from __future__ import annotations

import json
from pathlib import Path
import sqlite3
import sys
from tempfile import TemporaryDirectory
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "Driver"))
sys.path.insert(0, str(ROOT))

from collector.filtering.linkedin_filter import is_duplicate_preview
from collector.save_raw_page import save_content
from db.job_registry import insert_source_job
from db.migrate import migrate_database
from db.readable_text import save_collected_job
from GUI import client_data


class JobAvailabilityTest(unittest.TestCase):
    def setUp(self):
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        self.database = self.root / "jobs.sqlite"
        migrate_database(self.database)
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)

    def add_job(self, job_id, status="New", availability="available"):
        ref = insert_source_job(self.connection, "linkedin", job_id)
        save_collected_job(
            self.connection, "linkedin", job_id, "Collected vacancy text",
            source_url=f"https://www.linkedin.com/jobs/view/{job_id}/",
            title="Java Developer",
        )
        self.connection.execute(
            "INSERT INTO jobs (source_job_ref, status, job_interest) VALUES (?, ?, 50)",
            (ref, status),
        )
        self.connection.execute(
            "UPDATE source_jobs SET availability = ? WHERE id = ?", (availability, ref)
        )
        self.connection.commit()

    def test_failures_and_recovery_preserve_working_status(self):
        self.add_job("1", "Applied")
        for checks, expected in ((1, "unavailable"), (2, "unavailable"), (3, "deleted")):
            state = json.loads(client_data.record_linkedin_availability(
                self.database, "1", "unavailable"
            ))
            self.assertEqual(state, {"availability": expected, "unavailable_checks": checks})
        for outcome in ("closed", "available"):
            state = json.loads(client_data.record_linkedin_availability(
                self.database, "1", outcome
            ))
            self.assertEqual(state, {"availability": outcome, "unavailable_checks": 0})
        self.assertEqual(self.connection.execute("SELECT status FROM jobs").fetchone()[0], "Applied")

    def test_filter_and_recheck_include_legacy_closed(self):
        self.add_job("1")
        self.add_job("2", "Checked", "unavailable")
        self.add_job("3", "Closed", "closed")
        self.add_job("4", "New", "deleted")
        filters = {"statuses": ["New", "Checked"], "show_zero": True, "show_unavailable": False}
        rows = json.loads(client_data.load_jobs(self.database, json.dumps(filters)))
        self.assertEqual(len(rows), 1)
        filters["show_unavailable"] = True
        rows = json.loads(client_data.load_jobs(self.database, json.dumps(filters)))
        self.assertEqual(len(rows), 4)
        candidates = json.loads(client_data.load_linkedin_availability_candidates(self.database))
        self.assertEqual({r["source_job_id"] for r in candidates}, {"1", "2", "3"})

    def test_deleted_bypasses_dedup_but_insert_fails_before_touching_raw(self):
        self.add_job("1", availability="deleted")
        self.assertFalse(is_duplicate_preview({"job_id": "1"}, self.database))
        raw = self.root / "raw"
        (raw / "pages").mkdir(parents=True)
        original = raw / "pages/1.html"
        original.write_text("Original raw HTML", encoding="utf-8")
        with self.assertRaisesRegex(sqlite3.IntegrityError, "linkedin:1.*UNIQUE"):
            save_content(
                source="linkedin", url="https://www.linkedin.com/jobs/view/1/",
                content="About the job: replacement", out_dir=raw, ext="html",
                force=False, db_path=self.database,
            )
        self.assertEqual(original.read_text(encoding="utf-8"), "Original raw HTML")
        self.connection.execute("UPDATE source_jobs SET availability = 'available'")
        self.connection.commit()
        self.assertTrue(is_duplicate_preview({"job_id": "1"}, self.database))

    def test_migration_preserves_legacy_closed_status(self):
        with sqlite3.connect(":memory:") as connection:
            connection.executescript("""
                CREATE TABLE source_jobs (id INTEGER PRIMARY KEY);
                CREATE TABLE jobs (source_job_ref INTEGER, status TEXT);
                INSERT INTO source_jobs VALUES (1), (2);
                INSERT INTO jobs VALUES (1, 'Closed'), (2, 'Applied');
            """)
            connection.executescript((ROOT / "Driver/db/migrations/037_source_job_availability.sql")
                                     .read_text(encoding="utf-8"))
            self.assertEqual(connection.execute("""
                SELECT j.status, s.availability, s.unavailable_checks
                FROM jobs j JOIN source_jobs s ON s.id = j.source_job_ref ORDER BY s.id
            """).fetchall(), [("Closed", "closed", 0), ("Applied", "available", 0)])


if __name__ == "__main__":
    unittest.main()
