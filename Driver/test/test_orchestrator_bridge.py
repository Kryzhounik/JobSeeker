from __future__ import annotations

from contextlib import closing
import io
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
from orchestrator import python_bridge, processing
from db.job_registry import insert_source_job
from db.migrate import migrate_database
from db.readable_text import save_collected_job
from db.save import save_records
from collector.logging.linkedin_run_logger import start_collection_run, finish_collection_run
from test.test_json_validation import valid_analysis


class OrchestratorBridgeTest(unittest.TestCase):
    @patch("orchestrator.processing.migrate_database")
    @patch("orchestrator.processing.load_job_facts_input")
    @patch("orchestrator.processing.run")
    def test_exact_cefr_conflict_stops_before_persistence(self, proxy_run, load_input, migrate) -> None:
        load_input.return_value = {
            "readable_text": "English: C1",
            "script_language_facts": [{
                "name": "English", "level": "C1", "level_rank": 5,
                "raw_value": "English: C1", "exact": True,
            }],
        }
        for languages in (
            [{"name": "English", "level": "B2", "level_rank": 4, "raw_value": "English: C1"}],
            [{"name": "English", "level": "C1", "level_rank": 4, "raw_value": "English: C1"}],
            [],
        ):
            with self.subTest(languages=languages), tempfile.TemporaryDirectory() as directory:
                analysis = valid_analysis()
                analysis["languages"] = languages
                proxy_run.return_value = Result(
                    ["codex", "exec"], 0, "facts-thread", json.dumps(analysis), {}, [],
                )
                with patch.object(processing, "DATA_ROOT", Path(directory)):
                    with self.assertRaisesRegex(ValueError, "CEFR conflict for linkedin:123"):
                        python_bridge.run_job_facts("run-1", "linkedin", "123", "facts", None, "unused.sqlite")
                self.assertFalse((Path(directory) / "analyzed").exists())
        migrate.assert_not_called()

    @patch("orchestrator.processing.run")
    def test_agent_can_raise_inexact_script_level(self, proxy_run) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "jobs.sqlite"
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                insert_source_job(connection, "linkedin", "123")
                save_collected_job(
                    connection, "linkedin", "123", "Fluent English",
                    source_url="https://www.linkedin.com/jobs/view/123/", title="Java Developer",
                )
                connection.commit()
            analysis = valid_analysis()
            analysis["languages"] = [{
                "name": "English", "level": "C1", "level_rank": 5, "raw_value": "Fluent English",
            }]
            proxy_run.return_value = Result(
                ["codex", "exec"], 0, "facts-thread", json.dumps(analysis), {}, [],
            )
            with patch.object(processing, "DATA_ROOT", root):
                python_bridge.run_job_facts("run-1", "linkedin", "123", "facts", None, str(database))
                python_bridge.run_candidate_fit("run-1", "linkedin", "123", "fit", None, str(database))
            self.assertEqual(proxy_run.call_count, 1)
            self.assertEqual(json.loads(proxy_run.call_args.kwargs["input_text"]), {"readable_text": "Fluent English"})
            saved = json.loads((root / "analyzed/linkedin/123.json").read_text(encoding="utf-8"))
            self.assertEqual(saved["languages"], analysis["languages"])
            scored = json.loads((root / "scored/linkedin/123.json").read_text(encoding="utf-8"))
            self.assertEqual((scored["candidate_fit_percent"], scored["candidate_fit_reason_code"]), (0, "lang"))

    def test_start_analysis_records_effective_parallelism(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            python_bridge.start_analysis("parallel-run", 3, 5, str(database))
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual((3, 5), connection.execute(
                    "SELECT parallel_agents, vacancies_per_agent FROM analyzer_runs WHERE run_id=?",
                    ("parallel-run",),
                ).fetchone())

    @patch("orchestrator.processing.run")
    def test_analysis_to_save_uses_known_identity_without_model_metadata(self, proxy_run) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "jobs.sqlite"
            migrate_database(database)
            url = "https://www.linkedin.com/jobs/view/123/"
            body = "Java Developer\nEnglish B2\nRemote Moldova\n"
            stored_text = f"source: linkedin\nraw_file: Data/raw/123.html\nsource_url: {url}\n\n{body}"
            with closing(sqlite3.connect(database)) as connection:
                insert_source_job(connection, "linkedin", "123")
                save_collected_job(
                    connection, "linkedin", "123", stored_text,
                    source_url=url, title="Java Developer", company="Example",
                    location="Moldova", workplace="remote",
                )
                connection.commit()

            analysis = {
                "added_at": "2026-10-29", "title": "Java Developer",
                "company": "Example", "location": "Moldova",
                "remote_type": "remote", "remote_scope": "Moldova",
                "relocation": "NO", "seniority": "senior", "role": "backend",
                "salary": "", "summary": "Java backend role", "notes": "",
                "languages": [{"name": "English", "level": "B2", "level_rank": 4, "raw_value": "English B2"}],
                "technologies": [],
            }
            fit = {
                "candidate_fit_percent": 80, "candidate_fit_reason_code": "ok",
                "candidate_fit_reason": "Backend match",
            }
            proxy_run.side_effect = [
                Result(["codex", "exec"], 0, "facts-thread", json.dumps(analysis), {}, []),
                Result(["codex", "exec"], 0, "fit-thread", json.dumps(fit), {}, []),
            ]

            with patch.object(processing, "DATA_ROOT", root):
                self.assertEqual("facts-thread", python_bridge.run_job_facts(
                    "run-1", "linkedin", "123", "facts", None, str(database),
                ))
                request = json.loads(proxy_run.call_args.kwargs["input_text"])
                self.assertEqual(set(request), {"readable_text"})
                self.assertEqual(request["readable_text"], body)
                self.assertEqual(proxy_run.call_args.kwargs["context_paths"], (
                    DRIVER_ROOT / "analyzer/config/language_levels.ini",
                ))
                self.assertEqual("fit-thread", python_bridge.run_candidate_fit(
                    "run-1", "linkedin", "123", "fit", "fit-thread", str(database),
                ))
                python_bridge.run_job_interest("linkedin", "123", str(database))
                self.assertEqual(1, python_bridge.save_scored('[{"source":"linkedin","job_id":"123"}]', str(database)))
                self.assertEqual(0, python_bridge.save_scored('[{"source":"linkedin","job_id":"123"}]', str(database)))

            for stage in ("analyzed", "scored"):
                artifact = json.loads((root / stage / "linkedin" / "123.json").read_text(encoding="utf-8"))
                self.assertNotIn("source", artifact)
                self.assertNotIn("source_url", artifact)
            with patch("db.save.sys.stdin", io.StringIO(json.dumps(artifact))):
                self.assertEqual(save_records(
                    "-", database, DRIVER_ROOT / "db/schema.sql", "linkedin", True, ["123"],
                )[0][0], "save")
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(
                    connection.execute(
                        "SELECT source_job.source_job_id, source_job.processing_status, "
                        "text.source_url, text.readable_text, job.candidate_fit_percent "
                        "FROM jobs job JOIN source_jobs source_job ON source_job.id = job.source_job_ref "
                        "JOIN source_job_texts text ON text.source_job_ref = source_job.id"
                    ).fetchone(),
                    ("123", "SAVED", url, stored_text, 80),
                )

    @staticmethod
    def _batch(database: Path) -> dict:
        with closing(sqlite3.connect(database)) as connection:
            connection.row_factory = sqlite3.Row
            return dict(connection.execute("SELECT * FROM collection_runs WHERE run_id = 'batch-1'").fetchone())

    def test_batch_resumes_after_collection_or_processing_failure(self) -> None:
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
            finish_collection_run("batch-1", "blocked", "collection_error", 0, "timeout", database)
            self.assertEqual(
                [{"source": "linkedin", "job_id": "1", "title": "Java"}],
                processing.load_collected_scope("batch-1", database),
            )
            self.assertEqual("1", json.loads(
                python_bridge.load_collected_scope("batch-1", str(database))
            )[0]["job_id"])
            self.assertEqual("blocked", self._batch(database)["collection_status"])
            self.assertEqual(0, self._batch(database)["accepted_count"])
            finish_collection_run("batch-1", "complete", "limit_reached", 1, "", database)
            batch = self._batch(database)
            self.assertEqual(("pending", "Collection", "complete", None),
                             (batch["status"], batch["stage"], batch["collection_status"], batch["finished_at"]))
            self.assertEqual(1, len(json.loads(python_bridge.load_collected_scope("batch-1", str(database)))))
            python_bridge.update_batch("batch-1", "running", "Job facts", "", str(database))
            with self.assertRaisesRegex(ValueError, "already running"):
                python_bridge.load_collected_scope("batch-1", str(database))
            self.assertEqual("running", self._batch(database)["status"])

            python_bridge.update_batch("batch-1", "running", "Job facts", "", str(database))
            python_bridge.update_batch("batch-1", "failed", "", "Failed to extract facts", str(database))
            batch = self._batch(database)
            self.assertEqual(("failed", "Job facts"), (batch["status"], batch["stage"]))
            self.assertIsNotNone(batch["finished_at"])
            self.assertEqual("1", json.loads(python_bridge.load_collected_scope("batch-1", str(database)))[0]["job_id"])
            python_bridge.update_batch("batch-1", "running", "Job facts", "", str(database))
            self.assertIsNone(self._batch(database)["finished_at"])
            python_bridge.update_batch("batch-1", "complete", "Saving", "", str(database))
            self.assertEqual("pending", self._batch(database)["status"])
            self.assertIsNone(self._batch(database)["finished_at"])
            with closing(sqlite3.connect(database)) as connection:
                connection.execute("UPDATE source_jobs SET processing_status = 'SAVED'")
                connection.commit()
            python_bridge.update_batch("batch-1", "complete", "Saving", "", str(database))
            self.assertEqual("complete", self._batch(database)["status"])
            with self.assertRaisesRegex(ValueError, "already complete"):
                python_bridge.load_collected_scope("batch-1", str(database))
            self.assertEqual("complete", self._batch(database)["status"])

    def test_migration_preserves_collection_state_and_existing_links(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            start_collection_run("old-batch", {}, database)
            finish_collection_run("old-batch", "complete", "limit_reached", 1, "", database)
            with closing(sqlite3.connect(database)) as connection:
                insert_source_job(connection, "linkedin", "1", "CLEANED", collection_run_id="old-batch")
                connection.execute("ALTER TABLE collection_runs RENAME TO linkedin_collection_runs")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN status")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN stage")
                connection.execute("ALTER TABLE linkedin_collection_runs DROP COLUMN finished_at")
                connection.execute("ALTER TABLE linkedin_collection_runs RENAME COLUMN collection_status TO status")
                connection.execute("ALTER TABLE linkedin_collection_runs RENAME COLUMN collection_finished_at TO finished_at")
                connection.execute("DELETE FROM schema_migrations WHERE version IN ('042_batch_state', '043_pending_batch_status', '047_collection_runs')")
                connection.commit()
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(("failed", "complete", 1, "old-batch"), connection.execute(
                    "SELECT run.status, run.collection_status, run.collection_finished_at IS NOT NULL, job.collection_run_id "
                    "FROM collection_runs run JOIN source_jobs job ON job.collection_run_id = run.run_id"
                ).fetchone())
                connection.execute("UPDATE collection_runs SET status = 'pending'")
                self.assertEqual([], connection.execute("PRAGMA foreign_key_check").fetchall())

    @patch("orchestrator.processing.save_records")
    def test_saved_count_excludes_skipped_records(self, save) -> None:
        save.return_value = [("save", "new"), ("skip", "already present")]
        self.assertEqual(1, python_bridge.save_scored('[{"source":"linkedin","job_id":"1"},{"source":"linkedin","job_id":"2"}]', "unused.sqlite"))
        save.return_value = [("skip", "already present")]
        self.assertEqual(0, python_bridge.save_scored('[{"source":"linkedin","job_id":"2"}]', "unused.sqlite"))
        save.return_value = [("error", "failed")]
        with self.assertRaises(RuntimeError):
            python_bridge.save_scored('[{"source":"linkedin","job_id":"2"}]', "unused.sqlite")

    @patch("orchestrator.processing.run")
    def test_agent_filter_uses_existing_cli_proxy(self, proxy_run) -> None:
        proxy_run.return_value = Result(
            ["codex", "exec"],
            0,
            "thread-1",
            '{"nonrelevant_jobs":[{"source":"linkedin","job_id":"2"}]}',
            {},
            [],
        )

        response = python_bridge.run_agent_filter(
            "run-1",
            '[{"source":"linkedin","job_id":"2","title":"Marketing"}]',
            "jobs.sqlite",
        )

        self.assertEqual(response, '{"nonrelevant_jobs":[{"source":"linkedin","job_id":"2"}]}')
        request = proxy_run.call_args.kwargs
        self.assertEqual(request["run_id"], "run-1")
        self.assertEqual(request["operation"], "agent_filter")
        self.assertEqual(request["target"], "batch")
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
                '[{"source":"linkedin","job_id":"1"},{"source":"linkedin","job_id":"2"}]',
                '[{"source":"linkedin","job_id":"1"},{"source":"linkedin","job_id":"2"}]',
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
