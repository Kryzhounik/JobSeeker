"""Batch migration and source identity checks using only temporary application data."""
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from db.migrate import migrate_database
from db.job_registry import insert_source_job
from db.readable_text import save_collected_job
from db.semantic_duplicates import load_batch
from db.collection_runs import list_runs
from collector.logging.linkedin_run_logger import start_collection_run, finish_collection_run
from orchestrator import processing, python_bridge
from codex_proxy.backend import Result
from test.test_json_validation import valid_analysis


class MultisourceWorkflowTest(unittest.TestCase):
    def test_rename_preserves_batch_jobs_pages_and_foreign_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            start_collection_run("legacy", {"limit": 10}, database)
            finish_collection_run("legacy", "complete", "limit_reached", 2, "saved message", database)
            with closing(sqlite3.connect(database)) as connection:
                connection.execute("PRAGMA foreign_keys=ON")
                insert_source_job(connection, "linkedin", "123", "CLEANED", collection_run_id="legacy")
                connection.execute("INSERT INTO linkedin_collection_pages(run_id,sequence_no,start) VALUES ('legacy',1,0)")
                original = connection.execute("SELECT * FROM collection_runs").fetchall()
                connection.execute("ALTER TABLE collection_runs RENAME TO linkedin_collection_runs")
                connection.execute("DELETE FROM schema_migrations WHERE version='047_collection_runs'")
                connection.commit()
            migrate_database(database)
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(original, connection.execute("SELECT * FROM collection_runs").fetchall())
                self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())
                self.assertIsNone(connection.execute("SELECT name FROM sqlite_master WHERE name='linkedin_collection_runs'").fetchone())
                self.assertEqual("collection_runs", next(row for row in connection.execute("PRAGMA foreign_key_list(source_jobs)") if row[3] == "collection_run_id")[2])
                self.assertEqual("collection_runs", connection.execute("PRAGMA foreign_key_list(linkedin_collection_pages)").fetchone()[2])
                self.assertEqual(("legacy", 1), connection.execute("SELECT run_id,sequence_no FROM linkedin_collection_pages").fetchone())
                self.assertEqual("saved message", list_runs(connection)[0]["message"])

    def test_same_id_keeps_source_through_resume_statuses_and_relevance(self):
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            start_collection_run("mixed", {}, database)
            with closing(sqlite3.connect(database)) as connection:
                for source in ("linkedin", "justjoin"):
                    insert_source_job(connection, source, "123", "CLEANED", collection_run_id="mixed")
                    save_collected_job(connection, source, "123", "Java backend", title="Java",
                                       source_url=f"https://example.com/{source}/123")
                connection.commit()
            finish_collection_run("mixed", "complete", "done", 2, "", database)
            scope = processing.load_collected_scope("mixed", database)
            self.assertEqual(["linkedin", "justjoin"], [job["source"] for job in scope])
            rejected = [{"source": "justjoin", "job_id": "123"}]
            python_bridge.mark_relevance(str(database), json.dumps(scope), json.dumps(rejected))
            statuses = json.loads(python_bridge.load_statuses(json.dumps(scope), str(database)))
            self.assertEqual({("linkedin", "123"): "RELEVANT", ("justjoin", "123"): "NONRELEVANT"},
                             {(row["source"], row["job_id"]): row["status"] for row in statuses})
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(["linkedin", "justjoin"], [row["source"] for row in load_batch(connection, "mixed")])
            with patch.object(processing, "_save_source_scored", return_value=1) as save:
                self.assertEqual(2, python_bridge.save_scored(json.dumps(scope), str(database)))
                self.assertEqual([("linkedin", ["123"], database), ("justjoin", ["123"], database)],
                                 [call.args for call in save.call_args_list])

    @patch("orchestrator.processing.run")
    def test_agent_analysis_and_storage_keep_same_id_in_separate_sources(self, run):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "jobs.sqlite"
            migrate_database(database)
            scope = [{"source": source, "job_id": "123"} for source in ("linkedin", "justjoin")]
            with closing(sqlite3.connect(database)) as connection:
                for job in scope:
                    source = job["source"]
                    insert_source_job(connection, source, "123", "RELEVANT")
                    save_collected_job(connection, source, "123", f"Java backend {source}\nEnglish B2",
                                       source_url=f"https://example.com/{source}/123", title=f"Java {source}")
                connection.commit()
            analysis = valid_analysis()
            analysis["relocation"] = "NO"
            fit = {"candidate_fit_percent": 80, "candidate_fit_reason_code": "ok", "candidate_fit_reason": "Match"}
            run.side_effect = [Result([], 0, "facts", json.dumps(analysis), {}, []),
                               Result([], 0, "fit", json.dumps(fit), {}, [])] * 2
            with patch.object(processing, "DATA_ROOT", root):
                for job in scope:
                    source = job["source"]
                    python_bridge.run_job_facts("run", source, "123", "facts", None, str(database))
                    self.assertIn(source, json.loads(run.call_args.kwargs["input_text"])["readable_text"])
                    python_bridge.run_candidate_fit("run", source, "123", "fit", "fit", str(database))
                    python_bridge.run_job_interest(source, "123", str(database))
                self.assertEqual(2, python_bridge.save_scored(json.dumps(scope), str(database)))
            with closing(sqlite3.connect(database)) as connection:
                rows = connection.execute("SELECT s.source,s.source_job_id,s.processing_status,t.title "
                                          "FROM jobs j JOIN source_jobs s ON s.id=j.source_job_ref "
                                          "JOIN source_job_texts t ON t.source_job_ref=s.id ORDER BY s.source").fetchall()
                self.assertEqual([("justjoin", "123", "SAVED", "Java justjoin"),
                                  ("linkedin", "123", "SAVED", "Java linkedin")], rows)
