from __future__ import annotations

from pathlib import Path
import sqlite3
import sys
from tempfile import TemporaryDirectory
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.linkedin_filter import decide_preview
from db.migrate import migrate_database


class PreviewTitleDedupTest(unittest.TestCase):
    def setUp(self) -> None:
        directory = TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.database = Path(directory.name) / "jobs.sqlite"
        migrate_database(self.database)
        self.connection = sqlite3.connect(self.database)
        self.addCleanup(self.connection.close)
        self.connection.executescript("""
            INSERT INTO companies (id, name) VALUES (1, 'AgileEngine');
            INSERT INTO linkedin_collection_runs (run_id) VALUES ('current-run');
            INSERT INTO source_jobs (
                id, source, source_job_id, processing_status, collection_run_id
            ) VALUES (1, 'linkedin', '100', 'CLEANED', 'current-run');
            INSERT INTO source_job_texts (
                source_job_ref, readable_text, title, company_id
            ) VALUES (1, 'Java vacancy', 'Senior Java Developer ID96248', 1);
        """)

    def preview(self, title: str = "Senior Java Developer ID96248",
                company: str = "AgileEngine", job_id: str = "200") -> dict:
        return decide_preview(
            {"job_id": job_id, "title": title, "company": company},
            db_path=self.database,
        )

    def test_duplicate_is_skipped_before_analysis_and_in_current_run(self) -> None:
        result = self.preview("  SENIOR  Java Developer id96248  ", " agileengine ")
        self.assertEqual(result["preview_decision"], "skip")
        self.assertEqual(result["preview_rule"], "duplicate_company_title_id")
        self.assertEqual(self.preview(job_id="100")["preview_rule"], "duplicate_source_job_id")

    def test_other_companies_titles_and_ids_are_kept(self) -> None:
        for title, company in (
            ("Senior Java Developer ID96248", "Other Company"),
            ("Senior Java Developer ID96248", ""),
            ("Senior Java Developer ID96249", "AgileEngine"),
            ("Middle Java Developer ID96248", "AgileEngine"),
        ):
            with self.subTest(title=title, company=company):
                self.assertEqual(self.preview(title, company)["preview_decision"], "open")

    def test_only_explicit_id_markers_enable_title_dedup(self) -> None:
        for title, decision in (
            ("Senior Java Developer ID 96248", "skip"),
            ("Senior Java Developer ID:96248", "skip"),
            ("Senior Java Developer ID-96248", "skip"),
            ("Senior Java 21 Developer", "open"),
            ("Senior Java Developer 96248", "open"),
            ("Senior Java Developer CID96248", "open"),
        ):
            with self.subTest(title=title):
                self.connection.execute("UPDATE source_job_texts SET title = ?", (title,))
                self.connection.commit()
                self.assertEqual(self.preview(title)["preview_decision"], decision)

    def test_old_and_deleted_records_do_not_block_collection(self) -> None:
        self.connection.execute(
            "UPDATE linkedin_collection_runs SET started_at = datetime('now', '-8 days')"
        )
        self.connection.commit()
        self.assertEqual(self.preview()["preview_decision"], "open")
        self.connection.execute("UPDATE linkedin_collection_runs SET started_at = CURRENT_TIMESTAMP")
        self.connection.execute("UPDATE source_jobs SET availability = 'deleted'")
        self.connection.commit()
        self.assertEqual(self.preview()["preview_decision"], "open")

    def test_unfinished_raw_blocks_neither_id_nor_company_title(self) -> None:
        self.connection.execute("UPDATE source_jobs SET processing_status = 'RAW'")
        self.connection.commit()
        self.assertEqual(self.preview(job_id="100")["preview_decision"], "open")
        self.assertEqual(self.preview(job_id="200")["preview_decision"], "open")


if __name__ == "__main__":
    unittest.main()
