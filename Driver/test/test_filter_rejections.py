from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.filter_rejections import save_content_filter_rejection


class FilterRejectionsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        self.connection.execute("PRAGMA foreign_keys = ON")
        self.connection.executescript(
            """
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY,
                source TEXT NOT NULL,
                source_job_id TEXT NOT NULL,
                UNIQUE(source, source_job_id)
            );
            INSERT INTO source_jobs VALUES (1, 'linkedin', '123');
            CREATE TABLE content_filter_rejections (
                source_job_ref INTEGER NOT NULL
                    REFERENCES source_jobs(id) ON DELETE CASCADE,
                rule TEXT NOT NULL,
                matched_text TEXT NOT NULL,
                matched_keyword TEXT NOT NULL,
                matched_pattern TEXT NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (source_job_ref, matched_keyword, matched_pattern)
            );
            """
        )

    def tearDown(self) -> None:
        self.connection.close()

    def test_rejection_is_stored_in_structured_columns(self) -> None:
        save_content_filter_rejection(
            self.connection,
            "linkedin",
            "123",
            rule="hard_blocked_technology",
            matched_text="Deep expertise in Camunda 8",
            keyword_patterns=(("Camunda", r"deep\s+expertise.*{technology}"),),
        )
        self.assertEqual(
            self.connection.execute(
                """
                SELECT rule, matched_text, matched_keyword, matched_pattern
                FROM content_filter_rejections
                """
            ).fetchone(),
            (
                "hard_blocked_technology",
                "Deep expertise in Camunda 8",
                "Camunda",
                r"deep\s+expertise.*{technology}",
            ),
        )

if __name__ == "__main__":
    unittest.main()
