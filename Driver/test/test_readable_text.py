from __future__ import annotations

import sqlite3
import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.readable_text import has_readable_text
from db.readable_text import load_readable_text
from db.readable_text import save_readable_text


class ReadableTextTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        self.connection.execute("PRAGMA foreign_keys = ON")
        self.connection.executescript(
            """
            CREATE TABLE job_sources (code TEXT PRIMARY KEY);
            INSERT INTO job_sources (code) VALUES ('linkedin');
            CREATE TABLE processing_statuses (
                code TEXT PRIMARY KEY,
                sort_order INTEGER NOT NULL UNIQUE
            );
            INSERT INTO processing_statuses (code, sort_order) VALUES
                ('RAW', 10), ('CLEANED', 20), ('ANALYZED', 30),
                ('SCORED', 40), ('SAVED', 50);
            CREATE TABLE source_jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source TEXT NOT NULL REFERENCES job_sources(code),
                source_job_id TEXT NOT NULL,
                processing_status TEXT NOT NULL REFERENCES processing_statuses(code),
                UNIQUE(source, source_job_id)
            );
            CREATE TABLE source_job_texts (
                source_job_ref INTEGER PRIMARY KEY
                    REFERENCES source_jobs(id) ON DELETE CASCADE,
                readable_text TEXT NOT NULL CHECK (length(trim(readable_text)) > 0)
            );
            """
        )

    def tearDown(self) -> None:
        self.connection.close()

    def test_saves_loads_and_replaces_one_text_per_source_job(self) -> None:
        save_readable_text(self.connection, "linkedin", "123", "first")
        save_readable_text(self.connection, "linkedin", "123", "second")

        self.assertTrue(has_readable_text(self.connection, "linkedin", "123"))
        self.assertEqual(load_readable_text(self.connection, "linkedin", "123"), "second")
        self.assertEqual(
            self.connection.execute("SELECT count(*) FROM source_job_texts").fetchone()[0],
            1,
        )
        self.assertEqual(
            self.connection.execute(
                "SELECT processing_status FROM source_jobs"
            ).fetchone()[0],
            "CLEANED",
        )

    def test_rejects_empty_text(self) -> None:
        with self.assertRaises(ValueError):
            save_readable_text(self.connection, "linkedin", "123", "  ")

    def test_deleting_source_job_deletes_its_text(self) -> None:
        save_readable_text(self.connection, "linkedin", "123", "text")
        self.connection.execute("DELETE FROM source_jobs WHERE source_job_id = '123'")
        self.assertEqual(
            self.connection.execute("SELECT count(*) FROM source_job_texts").fetchone()[0],
            0,
        )


if __name__ == "__main__":
    unittest.main()
