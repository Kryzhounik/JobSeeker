from __future__ import annotations

from contextlib import closing
import json
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch
from zipfile import ZipFile


DRIVER_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = DRIVER_ROOT.parent
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.evals import run


class AnalyzerEvalRunnerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.project = self.root / "fixture-project"
        self.workspace = self.root / "evaluation" / "workspace"
        self.results = self.root / "evaluation" / "results"
        self.dataset = self.root / "dataset.sql"
        self._make_fixture_project()
        self.dataset.write_text(
            """
            INSERT INTO collection_runs (run_id) VALUES ('fixture-run');
            INSERT INTO source_jobs (
                source, source_job_id, processing_status, collection_run_id
            ) VALUES ('linkedin', 'test-job-1', 'RELEVANT', 'fixture-run');
            INSERT INTO source_job_texts (source_job_ref, readable_text)
            VALUES (last_insert_rowid(), 'Synthetic backend vacancy.');
            """,
            encoding="utf-8",
        )

    def _make_fixture_project(self) -> None:
        driver = self.project / "Driver"
        for name in ("analyzer", "common", "contracts", "db", "codex_proxy"):
            self._copy_project_component(DRIVER_ROOT / name, driver / name)
        for name in ("config", "filtering"):
            self._copy_project_component(
                DRIVER_ROOT / "collector" / name,
                driver / "collector" / name,
            )
        self._copy_project_component(
            DRIVER_ROOT / "orchestrator", driver / "orchestrator"
        )
        libs = self.project / "libs"
        libs.mkdir(parents=True)
        with ZipFile(libs / "tools-1.0-SNAPSHOT.jar", "w") as jar:
            jar.writestr("com/seeker/logging/LoggingAspect.class", b"")

        tools = self.project / "Tools"
        tools.mkdir()
        shutil.copy2(PROJECT_ROOT / "Tools" / "application_logging.py", tools)
        runtime = {
            "python.home": str(Path(sys.prefix).resolve()),
            "python.executable": str(Path(sys.executable).resolve()),
            "python.library": str(Path(sys.executable).resolve()),
            "jpy.library": str(Path(sys.executable).resolve()),
            "jdl.library": str(Path(sys.executable).resolve()),
            "database.path": "should-be-replaced.sqlite",
        }
        (tools / "runtime.properties").write_text(
            "".join(f"{key}={value}\n" for key, value in runtime.items()),
            encoding="utf-8",
        )
        proxy_config = driver / "codex_proxy" / "config.ini"
        proxy_config.write_text(
            "[proxy]\nmodel=fixture-model\nreasoning_effort=low\n",
            encoding="utf-8",
        )
        (driver / "analyzer" / "config" / "execution.ini").write_text(
            "[job_facts]\nworkers=1\n", encoding="utf-8"
        )

        profile = self.project / "Data" / "Profile"
        (profile / "Driver" / "analyzer").mkdir(parents=True)
        (profile / "resume.ini").write_text(
            "fixture_candidate=Candidate from isolated fixture\n", encoding="utf-8"
        )
        (profile / "Driver" / "analyzer" / "filter.ini").write_text(
            "fixture_filter=isolated\n", encoding="utf-8"
        )
        (profile / "Driver" / "analyzer" / "interest.ini").write_text(
            "fixture_interest=isolated\n", encoding="utf-8"
        )

    @staticmethod
    def _copy_project_component(source: Path, target: Path) -> None:
        shutil.copytree(
            source,
            target,
            ignore=shutil.ignore_patterns(
                "__pycache__", "*.pyc", "target", "node_modules", "evals"
            ),
        )

    def test_prepare_seeds_fresh_database_and_resolves_only_fixture_paths(self) -> None:
        actual_run = run.subprocess.run
        with patch.object(run.subprocess, "run", wraps=actual_run) as subprocess_run:
            prepared = run.prepare(self.dataset, self.workspace, self.project)

        database = Path(prepared["database"])
        self.assertEqual(database, self.workspace / "Data" / "jobs.sqlite")
        self.assertEqual(prepared["run_id"], "fixture-run")
        self.assertEqual(prepared["source"], "linkedin")
        self.assertEqual(prepared["job_ids"], ["test-job-1"])
        with closing(sqlite3.connect(database)) as connection:
            rows = connection.execute(
                """SELECT source_job_id, processing_status, collection_run_id
                   FROM source_jobs"""
            ).fetchall()
            text = connection.execute(
                "SELECT readable_text FROM source_job_texts"
            ).fetchone()[0]
        self.assertEqual(rows, [("test-job-1", "RELEVANT", "fixture-run")])
        self.assertEqual(text, "Synthetic backend vacancy.")

        copied_profile = self.workspace / "Data" / "Profile" / "resume.ini"
        self.assertIn("Candidate from isolated fixture", copied_profile.read_text())
        self.assertNotIn("should-be-replaced", (self.workspace / "Tools" / "runtime.properties").read_text())

        self.assertEqual(subprocess_run.call_count, 1)
        args, kwargs = subprocess_run.call_args
        self.assertEqual(Path(args[0][0]).resolve(), Path(sys.executable).resolve())
        self.assertIn("-c", args[0])
        self.assertNotIn("java", os.path.basename(args[0][0]).lower())
        self.assertEqual(Path(kwargs["cwd"]).resolve(), self.workspace.resolve())
        self.assertEqual(
            kwargs["env"]["PYTHONPATH"],
            os.pathsep.join((str(self.workspace.resolve()), str((self.workspace / "Driver").resolve()))),
        )
        self.assertEqual(kwargs["env"]["PYTHONNOUSERSITE"], "1")
        self.assertNotIn("PYTHONHOME", kwargs["env"])

    def test_repeated_prepare_resets_workspace_and_preserves_sibling_results(self) -> None:
        self.results.mkdir(parents=True)
        prior_result = self.results / "previous-run.txt"
        prior_result.write_text("retain this prior result", encoding="utf-8")

        run.prepare(self.dataset, self.workspace, self.project)
        with closing(sqlite3.connect(self.workspace / "Data" / "jobs.sqlite")) as connection:
            connection.execute(
                "UPDATE source_jobs SET processing_status='ANALYZED'"
            )
            connection.commit()
        (self.workspace / "stale-workspace-file").write_text("stale")

        run.prepare(self.dataset, self.workspace, self.project)

        self.assertEqual(prior_result.read_text(encoding="utf-8"), "retain this prior result")
        self.assertFalse((self.workspace / "stale-workspace-file").exists())
        with closing(sqlite3.connect(self.workspace / "Data" / "jobs.sqlite")) as connection:
            status = connection.execute(
                "SELECT processing_status FROM source_jobs WHERE source_job_id='test-job-1'"
            ).fetchone()[0]
        self.assertEqual(status, "RELEVANT")

    def test_rejects_dataset_without_required_relevant_vacancy(self) -> None:
        invalid_dataset = self.root / "invalid.sql"
        invalid_dataset.write_text(
            "INSERT INTO collection_runs (run_id) VALUES ('empty-run');",
            encoding="utf-8",
        )

        with self.assertRaisesRegex(ValueError, "readable RELEVANT vacancies"):
            run.prepare(invalid_dataset, self.workspace, self.project)

    def test_main_retains_failed_run_artifacts_and_separates_next_result(self) -> None:
        scores = self.dataset.parent / "external-scores.csv"
        scores.write_text("job_id,score\ntest-job-1,80\n", encoding="utf-8")
        jar = self.project / "Driver" / "orchestrator" / "workflow-orchestrator.jar"
        jar.write_bytes(b"fixture jar")
        self.results.mkdir(parents=True)
        java_commands: list[list[str]] = []
        actual_run = run.subprocess.run

        def fake_subprocess(args, **kwargs):
            if Path(args[0]).resolve() == Path(sys.executable).resolve():
                return actual_run(args, **kwargs)
            if args[0] == "git":
                return subprocess.CompletedProcess(args, 0, "fixture-commit\n", "")
            if args[0] == "fake-java":
                java_commands.append(args)
                if len(java_commands) == 2:
                    raise subprocess.CalledProcessError(1, args)
                return subprocess.CompletedProcess(args, 0, "", "")
            self.fail(f"Unexpected subprocess: {args[0]}")

        original_root = run.ROOT
        original_eval_root = run.EVAL_ROOT
        run.ROOT = self.project
        run.EVAL_ROOT = self.root / "evaluation"
        self.addCleanup(setattr, run, "ROOT", original_root)
        self.addCleanup(setattr, run, "EVAL_ROOT", original_eval_root)

        with patch.object(run.subprocess, "run", side_effect=fake_subprocess), patch.object(
            sys, "argv", ["run.py", str(self.dataset), "--java", "fake-java"]
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                run.main()

        result_directories = list(self.results.iterdir())
        self.assertEqual(len(result_directories), 1)
        failed_result = result_directories[0]
        self.assertEqual((failed_result / "external-scores.csv").read_bytes(), scores.read_bytes())
        prompt = Path("Driver/common/prompts/untrusted_content.md")
        self.assertEqual((failed_result / "inputs" / prompt).read_bytes(),
                         (self.project / prompt).read_bytes())
        self.assertTrue((failed_result / "metadata.json").is_file())
        self.assertTrue((failed_result / "Data" / "jobs.sqlite").is_file())
        self.assertTrue((failed_result / "Profile" / "resume.ini").is_file())
        self.assertTrue((failed_result / "job-facts.stdout.log").is_file())
        self.assertTrue((failed_result / "finish-analysis.stderr.log").is_file())

        with patch.object(run.subprocess, "run", side_effect=fake_subprocess), patch.object(
            sys, "argv", ["run.py", str(self.dataset), "--java", "fake-java"]
        ):
            run.main()

        all_results = list(self.results.iterdir())
        self.assertEqual(len(all_results), 2)
        self.assertTrue((failed_result / "Data" / "jobs.sqlite").is_file())
        self.assertEqual(
            [Path(args[2]).name for args in java_commands],
            ["workflow-orchestrator.jar"] * 4,
        )
        self.assertEqual(
            [args[3] for args in java_commands],
            ["job-facts", "finish-analysis", "job-facts", "finish-analysis"],
        )
        self.assertEqual(
            [args[4:] for args in java_commands],
            [
                ["fixture-run", "linkedin", "test-job-1"],
                ["fixture-run", "linkedin", "test-job-1"],
                ["fixture-run", "linkedin", "test-job-1"],
                ["fixture-run", "linkedin", "test-job-1"],
            ],
        )
        metadata = json.loads((failed_result / "metadata.json").read_text(encoding="utf-8"))
        self.assertEqual(metadata["model"], "fixture-model")
        self.assertEqual(metadata["job_ids"], ["test-job-1"])


if __name__ == "__main__":
    unittest.main()
