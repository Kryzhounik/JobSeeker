from __future__ import annotations

import json
from pathlib import Path
import sqlite3
import sys
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.linkedin_filter import FilterResult, is_duplicate_preview
from collector.java_linkedin import python_bridge
from collector.save_raw_page import save_content


class RawCollectionRetryTest(unittest.TestCase):
    def setUp(self) -> None:
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.database = Path(directory.name) / "jobs.sqlite"
        self.raw = Path(directory.name) / "raw"
        python_bridge.init_session(str(DRIVER_ROOT), str(self.database), str(self.raw))
        self.addCleanup(python_bridge.close_session)
        python_bridge.start_run("first-run", "{}")
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)
        self.preview = {"job_id": "7777777777",
                        "source_url": "https://www.linkedin.com/jobs/view/7777777777/",
                        "title": "Senior Java Developer ID96248", "company": "Example"}
        self.original = "<h2>About the job</h2><p>Original Java services.</p>"
        self.fresh = "<h2>About the job</h2><p>Fresh Java services.</p>"

    def save(self, content: str) -> Path:
        return save_content(
            source="linkedin", url=self.preview["source_url"], content=content,
            out_dir=self.raw, ext="html", force=False, db_path=self.database,
            collection_method="playwright", collection_run_id="first-run",
        )

    def test_failed_filter_can_be_retried_with_fresh_html_and_current_run(self) -> None:
        preview_json = json.dumps(self.preview)
        vacancy_filter = python_bridge._require_session().vacancy_filter
        with patch.object(vacancy_filter, "filter_text", side_effect=RuntimeError("filter failed")):
            with self.assertRaisesRegex(RuntimeError, "filter failed"):
                python_bridge.process_html(preview_json, self.original, False)
        original_row = self.connection.execute(
            "SELECT id, processing_status, collection_run_id FROM source_jobs"
        ).fetchone()
        self.assertEqual(original_row[1:], ("RAW", "first-run"))
        self.assertEqual(0, self.connection.execute("SELECT count(*) FROM source_job_texts").fetchone()[0])

        python_bridge.start_run("retry-run", "{}")
        self.assertEqual("open", json.loads(python_bridge.decide_preview(preview_json))["preview_decision"])
        with patch.object(vacancy_filter, "filter_text", return_value=FilterResult(False, "passed")) as content_filter:
            result = json.loads(python_bridge.process_html(preview_json, self.fresh, False))

        self.assertEqual("raw_saved", result["status"])
        self.assertEqual([(original_row[0], "CLEANED", "retry-run")], self.connection.execute(
            "SELECT id, processing_status, collection_run_id FROM source_jobs").fetchall())
        self.assertEqual(self.fresh, (self.raw / "pages/7777777777.html").read_text(encoding="utf-8"))
        text = self.connection.execute("SELECT readable_text FROM source_job_texts").fetchone()[0]
        self.assertIn("Fresh Java services.", text)
        self.assertNotIn("Original Java services.", text)
        content_filter.assert_called_once_with(self.preview["title"], text)

    def test_processed_and_deleted_records_cannot_be_overwritten(self) -> None:
        raw_path = self.save(self.original)
        states = [(status, "available") for status in (
            "CLEANED", "CONTENT_REJECTED", "RELEVANT", "NONRELEVANT", "ANALYZED", "SCORED", "SAVED"
        )] + [("RAW", "deleted")]
        for status, availability in states:
            with self.subTest(status=status, availability=availability):
                self.connection.execute(
                    "UPDATE source_jobs SET processing_status = ?, availability = ?", (status, availability)
                )
                self.connection.commit()
                self.assertEqual(availability != "deleted", is_duplicate_preview(self.preview, self.database))
                with self.assertRaisesRegex(sqlite3.IntegrityError, "Cannot insert source job linkedin:7777777777"):
                    self.save(self.fresh)
                self.assertEqual(self.original, raw_path.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
