from __future__ import annotations

from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.backend import Result
from orchestrator import python_bridge
from db.job_registry import insert_source_job
from db.migrate import migrate_database
from collector.logging.linkedin_run_logger import start_collection_run, finish_collection_run


class OrchestratorBridgeTest(unittest.TestCase):
    @staticmethod
    def _batch(database: Path) -> dict:
        with closing(sqlite3.connect(database)) as connection:
            connection.row_factory = sqlite3.Row
            return dict(connection.execute("SELECT * FROM linkedin_collection_runs WHERE run_id = 'batch-1'").fetchone())

    def test_batch_continues_after_collection_limit_and_can_resume_after_failure(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            start_collection_run("batch-1", {}, database)
            with closing(sqlite3.connect(database)) as connection:
                insert_source_job(connection, "linkedin", "1", "CLEANED", collection_run_id="batch-1")
                connection.execute(
                    "INSERT INTO source_job_texts (source_job_ref, readable_text, title) "
                    "SELECT id, 'Job description', 'Java' FROM source_jobs"
                )
                connection.commit()
            finish_collection_run("batch-1", "complete", "limit_reached", 1, "", database)
            batch = self._batch(database)
            self.assertEqual(("running", "Collection", "complete", None),
                             (batch["status"], batch["stage"], batch["collection_status"], batch["finished_at"]))

            python_bridge.update_batch("batch-1", "running", "Job facts", "", str(database))
            python_bridge.update_batch("batch-1", "failed", "", "Failed to extract facts", str(database))
            batch = self._batch(database)
            self.assertEqual(("failed", "Job facts"), (batch["status"], batch["stage"]))
            self.assertIsNotNone(batch["finished_at"])
            self.assertEqual("1", json.loads(python_bridge.load_collected_scope("batch-1", str(database)))[0]["job_id"])
            python_bridge.update_batch("batch-1", "running", "Job facts", "", str(database))
            self.assertIsNone(self._batch(database)["finished_at"])
            python_bridge.update_batch("batch-1", "complete", "Saving", "", str(database))
            self.assertEqual("running", self._batch(database)["status"])
            with closing(sqlite3.connect(database)) as connection:
                connection.execute("UPDATE source_jobs SET processing_status = 'SAVED'")
                connection.commit()
            python_bridge.update_batch("batch-1", "complete", "Saving", "", str(database))
            self.assertEqual("complete", self._batch(database)["status"])

    def test_migration_preserves_collection_state_and_existing_links(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            start_collection_run("old-batch", {}, database)
            finish_collection_run("old-batch", "complete", "limit_reached", 1, "", database)
            with closing(sqlite3.connect(database)) as connection:
                insert_source_job(connection, "linkedin", "1", "CLEANED", collection_run_id="old-batch")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN status")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN stage")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN finished_at")
                connection.execute("ALTER TABLE linkedin_collection_runs RENAME COLUMN collection_status TO status")
                connection.execute("ALTER TABLE linkedin_collection_runs RENAME COLUMN collection_finished_at TO finished_at")
                connection.execute("DELETE FROM schema_migrations WHERE version = '042_batch_state'")
                connection.commit()
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(("running", "complete", 1, "old-batch"), connection.execute(
                    "SELECT run.status, run.collection_status, run.collection_finished_at IS NOT NULL, job.collection_run_id "
                    "FROM linkedin_collection_runs run JOIN source_jobs job ON job.collection_run_id = run.run_id"
                ).fetchone())

    @patch("orchestrator.python_bridge.save_records")
    def test_saved_count_excludes_skipped_records(self, save) -> None:
        save.return_value = [("save", "new"), ("skip", "already present")]
        self.assertEqual(1, python_bridge.save_scored("linkedin", '["1","2"]', "unused.sqlite"))
        save.return_value = [("skip", "already present")]
        self.assertEqual(0, python_bridge.save_scored("linkedin", '["2"]', "unused.sqlite"))
        save.return_value = [("error", "failed")]
        with self.assertRaises(RuntimeError):
            python_bridge.save_scored("linkedin", '["2"]', "unused.sqlite")

    @patch("orchestrator.python_bridge.run")
    def test_agent_filter_uses_existing_cli_proxy(self, proxy_run) -> None:
        proxy_run.return_value = Result(
            ["codex", "exec"],
            0,
            "thread-1",
            '{"nonrelevant_job_ids":["2"]}',
            {},
            [],
        )

        response = python_bridge.run_agent_filter(
            "run-1",
            '[{"source":"linkedin","job_id":"2","title":"Marketing"}]',
            "jobs.sqlite",
        )

        self.assertEqual(response, '{"nonrelevant_job_ids":["2"]}')
        request = proxy_run.call_args.kwargs
        self.assertEqual(request["run_id"], "run-1")
        self.assertEqual(request["operation"], "agent_filter")
        self.assertEqual(request["target"], "linkedin:batch")
        self.assertEqual(request["context_paths"], ())
        self.assertEqual(
            request["instruction_path"],
            DRIVER_ROOT / "analyzer" / "agent_filter.md",
        )
        self.assertEqual(
            request["output_schema"],
            DRIVER_ROOT / "contracts" / "agent_filter_result.schema.json",
        )

    def test_mark_relevance_updates_all_ids_in_one_database(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                for job_id in ("1", "2"):
                    insert_source_job(connection, "linkedin", job_id, "CLEANED")
                connection.commit()

            python_bridge.mark_relevance(
                str(database),
                "linkedin",
                '["1","2"]',
                '["1","2"]',
            )

            with closing(sqlite3.connect(database)) as connection:
                rows = connection.execute(
                    """
                    SELECT source_job_id, processing_status
                    FROM source_jobs
                    ORDER BY source_job_id
                    """
                ).fetchall()
            self.assertEqual(rows, [("1", "NONRELEVANT"), ("2", "NONRELEVANT")])


if __name__ == "__main__":
    unittest.main()
