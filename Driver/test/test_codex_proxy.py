from __future__ import annotations

from contextlib import closing
from pathlib import Path
import sqlite3
from types import SimpleNamespace
import sys
import tempfile
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.backend import Result
from codex_proxy import codex_cli
from codex_proxy.metrics_proxy import cli_continuation_prompt
from codex_proxy.metrics_proxy import cli_prompt
from codex_proxy.metrics import save
from codex_proxy.output_schema import codex_schema
from codex_proxy.settings import load


class CodexProxyTest(unittest.TestCase):
    def test_operation_model_settings_override_shared_defaults_independently(self) -> None:
        settings = load(
            model_override="gpt-6-luna",
            reasoning_effort_override="max",
        )

        self.assertEqual(settings.model, "gpt-6-luna")
        self.assertEqual(settings.reasoning_effort, "max")

        default_settings = load()
        model_only = load(model_override="gpt-6-astra")
        self.assertEqual(model_only.model, "gpt-6-astra")
        self.assertEqual(
            model_only.reasoning_effort,
            default_settings.reasoning_effort,
        )

    def test_job_analysis_schema_is_adapted_for_structured_output(self) -> None:
        schema = codex_schema(DRIVER_ROOT / "contracts" / "job_analysis.schema.json")

        self.assertEqual(schema["type"], "object")
        self.assertFalse(schema["additionalProperties"])
        self.assertNotIn("allOf", schema)
        self.assertNotIn("not", schema["properties"]["salary"])

    def test_cli_prompt_materializes_only_explicit_operation_files(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            instruction = root / "instruction.md"
            context = root / "context.ini"
            instruction.write_text("FIT RULES", encoding="utf-8")
            context.write_text("CANDIDATE PROFILE", encoding="utf-8")

            prompt = cli_prompt(
                instruction,
                '{"title":"Backend"}',
                "stdin",
                (context,),
            )

            self.assertIn("FIT RULES", prompt)
            self.assertIn('{"title":"Backend"}', prompt)
            self.assertIn("CANDIDATE PROFILE", prompt)

    def test_cli_continuation_sends_only_the_next_input(self) -> None:
        prompt = cli_continuation_prompt('{"job_id":"2"}', "linkedin-2.json")

        self.assertIn('{"job_id":"2"}', prompt)
        self.assertIn("linkedin-2.json", prompt)
        self.assertNotIn("INSTRUCTION", prompt)
        self.assertNotIn("CONTEXT", prompt)

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_cli_starts_persisted_session(self, process_run, unused_which) -> None:
        process_run.return_value = SimpleNamespace(
            returncode=0,
            stdout=(
                '{"type":"thread.started","thread_id":"thread-1"}\n'
                '{"type":"item.completed","item":'
                '{"type":"agent_message","text":"{}"}}\n'
            ),
            stderr="",
        )
        settings = load()

        result = codex_cli.call(
            config=settings.config,
            prompt="PROMPT",
            model=settings.model,
            reasoning_effort=settings.reasoning_effort,
            cwd=DRIVER_ROOT,
            output_schema=DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
        )

        command = process_run.call_args.args[0]
        self.assertEqual(command[1:2], ["exec"])
        self.assertNotIn("resume", command)
        self.assertNotIn("--ephemeral", command)
        self.assertIn("--sandbox", command)
        self.assertEqual(result.thread_id, "thread-1")

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_cli_resumes_persisted_session(self, process_run, unused_which) -> None:
        process_run.return_value = SimpleNamespace(
            returncode=0,
            stdout=(
                '{"type":"item.completed","item":'
                '{"type":"agent_message","text":"{}"}}\n'
            ),
            stderr="",
        )
        settings = load()

        result = codex_cli.call(
            config=settings.config,
            prompt="NEXT INPUT",
            model=settings.model,
            reasoning_effort=settings.reasoning_effort,
            cwd=DRIVER_ROOT,
            output_schema=DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
            thread_id="thread-1",
        )

        command = process_run.call_args.args[0]
        self.assertEqual(command[1:3], ["exec", "resume"])
        self.assertIn("thread-1", command)
        self.assertNotIn("--sandbox", command)
        self.assertNotIn("--add-dir", command)
        self.assertEqual(result.thread_id, "thread-1")

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    def test_cli_rejects_resume_in_ephemeral_mode(self, unused_which) -> None:
        settings = load()
        settings.config.set("proxy", "ephemeral", "true")

        with self.assertRaisesRegex(ValueError, "ephemeral mode"):
            codex_cli.call(
                config=settings.config,
                prompt="NEXT INPUT",
                model=settings.model,
                reasoning_effort=settings.reasoning_effort,
                cwd=DRIVER_ROOT,
                output_schema=(
                    DRIVER_ROOT / "contracts" / "job_analysis.schema.json"
                ),
                thread_id="thread-1",
            )

    def test_metrics_are_aggregated_by_operation_inside_run(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            db_path = Path(directory) / "jobs.sqlite"
            common = {
                "db_path": db_path,
                "run_id": "run-1",
                "model": "gpt-test",
                "reasoning_effort": "medium",
                "rates": (1.0, 0.1, 6.0),
                "started_at": "2026-07-31T00:00:00.000+00:00",
                "finished_at": "2026-07-31T00:00:01.000+00:00",
            }

            def result(
                thread_id: str,
                input_tokens: int,
                cached_input_tokens: int,
                output_tokens: int,
                reasoning_output_tokens: int,
            ) -> Result:
                return Result(
                    ["codex", "exec"],
                    0,
                    thread_id,
                    "{}",
                    {
                        "input_tokens": input_tokens,
                        "cached_input_tokens": cached_input_tokens,
                        "output_tokens": output_tokens,
                        "reasoning_output_tokens": reasoning_output_tokens,
                    },
                    [],
                )

            save(
                operation="job_facts",
                target="1",
                duration_ms=1000,
                result=result("job-thread", 100, 50, 10, 2),
                **common,
            )
            save(
                operation="job_facts",
                target="2",
                duration_ms=3000,
                result=result("job-thread", 220, 130, 16, 3),
                **common,
            )
            save(
                operation="candidate_fit",
                target="1",
                duration_ms=2000,
                result=result("fit-thread", 80, 20, 8, 1),
                **common,
            )

            with closing(sqlite3.connect(db_path)) as connection:
                rows = connection.execute(
                    """
                    SELECT operation, invocation_count, duration_ms_sum,
                           input_tokens_sum, input_tokens_avg,
                           cached_input_tokens_sum, output_tokens_sum,
                           reasoning_output_tokens_sum
                    FROM codex_run_operations
                    WHERE run_id = ?
                    ORDER BY operation
                    """,
                    ("run-1",),
                ).fetchall()

            self.assertEqual(rows, [
                ("candidate_fit", 1, 2000, 80, 80.0, 20, 8, 1),
                ("job_facts", 2, 4000, 220, 110.0, 130, 16, 3),
            ])

            with closing(sqlite3.connect(db_path)) as connection:
                run = connection.execute(
                    """
                    SELECT input_tokens_sum, cached_input_tokens_sum,
                           output_tokens_sum, reasoning_output_tokens_sum
                    FROM codex_runs WHERE run_id = ?
                    """,
                    ("run-1",),
                ).fetchone()
                snapshots = connection.execute(
                    """
                    SELECT input_tokens
                    FROM codex_invocations
                    WHERE run_id = ? AND operation = 'job_facts'
                    ORDER BY id
                    """,
                    ("run-1",),
                ).fetchall()

            self.assertEqual(run, (300, 150, 24, 4))
            self.assertEqual(snapshots, [(100,), (220,)])


if __name__ == "__main__":
    unittest.main()
