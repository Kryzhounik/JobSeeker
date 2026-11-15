from contextlib import closing
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

DRIVER_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DRIVER_ROOT))
from collector.logging.collection_logger import record_collection_event
from collector.logging.linkedin_logger import record_collection_event as legacy_record_event
from collector.service import CollectorService
from db.migrate import migrate_database


class CollectionEventsTest(unittest.TestCase):
    def test_shared_service_keeps_source_for_outcomes_and_processed_vacancies(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            service = CollectorService(DRIVER_ROOT, database, Path(directory) / "raw")
            for source in ("linkedin", "justjoin"):
                preview = {"source": source, "job_id": "123", "title": "Java Developer",
                           "source_url": "https://www.linkedin.com/jobs/view/123/" if source == "linkedin"
                           else "https://justjoin.it/job-offer/123"}
                service.log_outcome(preview, "preview_filtered", "duplicate source_job_id")
                service.save_raw(preview, "<h2>About the job</h2><p>Java backend services.</p>" if source == "linkedin"
                                 else '{"body":"Java backend services."}', "html" if source == "linkedin" else "json", "script")
                result = service.process_vacancy({"preview": preview, "description": "Build Java backend services."})
                self.assertEqual("raw_saved", result["status"])
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual([
                    ("linkedin", "123", "preview_filtered"), ("linkedin", "123", "raw_saved"),
                    ("justjoin", "123", "preview_filtered"), ("justjoin", "123", "raw_saved"),
                ], connection.execute("SELECT source,job_id,status FROM collection_events ORDER BY id").fetchall())
                self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())

    def test_migration_preserves_events_and_recovers_preexisting_justjoin_source(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                connection.execute("DROP TABLE collection_events")
                connection.execute("""CREATE TABLE linkedin_collection_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT NOT NULL DEFAULT '', start INTEGER,
                    card_index INTEGER, job_id TEXT NOT NULL DEFAULT '', source_url TEXT NOT NULL DEFAULT '',
                    title TEXT NOT NULL DEFAULT '', company TEXT NOT NULL DEFAULT '', status TEXT NOT NULL,
                    reason TEXT NOT NULL DEFAULT '', created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                )""")
                connection.executemany("INSERT INTO linkedin_collection_events(id,label,job_id,source_url,status,reason,created_at) "
                                       "VALUES (?,?,?,?,?,?,?)", [
                    (5, "general", "123", "https://www.linkedin.com/jobs/view/123/", "raw_saved", "", "2026-11-01"),
                    (8, "", "123", "https://justjoin.it/job-offer/123", "raw_saved", "", "2026-11-02"),
                    (9, "justjoin", "124", "", "processing_failed", "error", "2026-11-03"),
                ])
                connection.execute("DELETE FROM schema_migrations WHERE version='048_collection_events'")
                connection.commit()
            # Direct logging must migrate a legacy database too, not create a parallel table.
            legacy_record_event({"job_id": "125", "status": "open_failed"}, database)
            record_collection_event({"preview": {"source": "justjoin", "job_id": "125"},
                                     "status": "preview_filtered"}, database)
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual([
                    (5, "linkedin", "123", "", "2026-11-01"),
                    (8, "justjoin", "123", "", "2026-11-02"),
                    (9, "justjoin", "124", "error", "2026-11-03"),
                ], connection.execute("SELECT id,source,job_id,reason,created_at FROM collection_events WHERE id<=9 ORDER BY id").fetchall())
                self.assertEqual([("linkedin", "125"), ("justjoin", "125")],
                                 connection.execute("SELECT source,job_id FROM collection_events WHERE id>9 ORDER BY id").fetchall())
                self.assertIsNone(connection.execute("SELECT name FROM sqlite_master WHERE name='linkedin_collection_events'").fetchone())
                indexes = {row[1] for row in connection.execute("PRAGMA index_list(collection_events)")}
                self.assertIn("idx_collection_events_source_job_id", indexes)
