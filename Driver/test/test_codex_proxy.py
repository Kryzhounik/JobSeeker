from __future__ import annotations

from contextlib import closing
from pathlib import Path
import sqlite3
import json
import os
from types import SimpleNamespace
import sys
import tempfile
import tomllib
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


def usage_result(
    thread_id: str,
    input_tokens: int,
    cached_input_tokens: int,
    output_tokens: int,
    reasoning_output_tokens: int,
    *,
    exit_code: int = 0,
) -> Result:
    return Result(
        ["codex", "exec"],
        exit_code,
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


def cli_config_overrides(command: list[str]) -> dict[str, object]:
    overrides = {}
    for index, argument in enumerate(command):
        if argument == "--config":
            key, value = command[index + 1].split("=", 1)
            overrides[key] = tomllib.loads("value = " + value)["value"]
        elif argument == "--disable":
            overrides["features." + command[index + 1]] = False
    return overrides


class CodexProxyTest(unittest.TestCase):
    def assert_lean_context_overrides(self, command: list[str]) -> None:
        overrides = cli_config_overrides(command)
        self.assertEqual(
            Path(overrides["model_instructions_file"]),
            DRIVER_ROOT / "codex_proxy" / "transport_instructions.md",
        )
        for key, expected in {
            "skills.include_instructions": False,
            "skills.bundled.enabled": False,
            "agents.enabled": False,
            "project_doc_max_bytes": 0,
            "web_search": "disabled",
            "include_collaboration_mode_instructions": False,
            "include_permissions_instructions": False,
            "include_apps_instructions": False,
            "features.shell_tool": False,
            "features.shell_snapshot": False,
            "features.multi_agent": False,
            "features.apps": False,
            "features.plugins": False,
            "features.skill_search": False,
            "features.goals": False,
            "features.sleep_tool": False,
            "features.view_image": False,
            "features.image_generation": False,
            "features.computer_use": False,
            "features.browser_use": False,
            "features.workspace_dependencies": False,
            "features.tool_suggest": False,
            "tools.update_plan.enabled": False,
            "tools.experimental_request_user_input.enabled": False,
        }.items():
            self.assertEqual(overrides.get(key), expected, key)

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
        self.assertNotIn("source", schema["properties"])
        self.assertNotIn("source_url", schema["properties"])

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
            self.assertIn("Do not use tools", prompt)

            browser_prompt = cli_prompt(
                instruction,
                '{"title":"Backend"}',
                "stdin",
                (context,),
                allow_browser_tools=True,
            )
            self.assertIn("Use only Playwright MCP browser tools", browser_prompt)
            self.assertNotIn("Do not use tools", browser_prompt)

    def test_cli_continuation_sends_only_the_next_input(self) -> None:
        prompt = cli_continuation_prompt('{"job_id":"2"}', "linkedin-2.json")

        self.assertIn('{"job_id":"2"}', prompt)
        self.assertIn("linkedin-2.json", prompt)
        self.assertNotIn("INSTRUCTION", prompt)
        self.assertNotIn("CONTEXT", prompt)
        self.assertIn("Do not use tools", prompt)

        browser_prompt = cli_continuation_prompt(
            '{"job_id":"2"}',
            "linkedin-2.json",
            allow_browser_tools=True,
        )
        self.assertIn("Use only Playwright MCP browser tools", browser_prompt)
        self.assertNotIn("Do not use tools", browser_prompt)

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_failed_cli_preserves_full_diagnostics_without_credentials(self, process_run, unused_which) -> None:
        response = {"type": "http.response", "status": 429, "request_id": "req-test", "headers": {
            "Authorization": "Bearer fake-bearer-secret", "Retry-After": "9",
            "Set-Cookie": "session=fake-cookie-secret; other=fake-other-cookie",
            "X-Api-Key": "fake-header-key",
        }}
        output = json.dumps(response) + '\n{"type":"error","message":"429 Too Many Requests"}\n'
        stderr = (json.dumps({"transport": json.dumps({"refresh_token": "fake-refresh-secret"})})
                  + '\nCookie: session=fake-stderr-cookie; other=fake-more-cookies\n'
                  + 'DEBUG request-id=req-test Retry-After=9 fake-env-secret\n')
        process_run.return_value = SimpleNamespace(returncode=1, stdout=output, stderr=stderr)
        settings = load()
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(codex_cli, "DATA_ROOT", Path(directory)), \
                patch.dict(os.environ, {"OPENAI_API_KEY": "fake-env-secret", "RUST_LOG": "info"}):
            result = codex_cli.call(
                config=settings.config, prompt="PROMPT", model=settings.model,
                reasoning_effort=settings.reasoning_effort, cwd=DRIVER_ROOT,
                output_schema=DRIVER_ROOT / "contracts/job_analysis.schema.json",
            )
            log = next(Path(directory).glob("logs/codex/*"))
            stdout = (log / "stdout.log").read_text(encoding="utf-8")
            saved_stderr = (log / "stderr.log").read_text(encoding="utf-8")
            self.assertEqual(json.loads(stdout.splitlines()[0])["request_id"], "req-test")
            self.assertIn("Retry-After", stdout)
            self.assertIn("Retry-After=9", saved_stderr)
            for secret in ("fake-bearer-secret", "fake-cookie-secret", "fake-other-cookie", "fake-header-key",
                           "fake-refresh-secret", "fake-stderr-cookie", "fake-more-cookies", "fake-env-secret"):
                self.assertNotIn(secret, stdout + saved_stderr + "\n".join(result.errors))
            self.assertIn(f"CLI diagnostics: {log}", result.errors)
            self.assertFalse(any("DEBUG" in error for error in result.errors))
            self.assertIn("codex_api=debug", process_run.call_args.kwargs["env"]["RUST_LOG"])
            self.assertEqual(process_run.call_args.kwargs["input"], "PROMPT")
            self.assertEqual(os.environ["RUST_LOG"], "info")
            self.assertEqual(process_run.call_count, 1)

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_successful_cli_does_not_save_debug_logs_or_error_text(self, process_run, unused_which) -> None:
        process_run.return_value = SimpleNamespace(returncode=0, stdout="", stderr="DEBUG transport details")
        settings = load()
        with tempfile.TemporaryDirectory() as directory, patch.object(codex_cli, "DATA_ROOT", Path(directory)):
            result = codex_cli.call(
                config=settings.config, prompt="PROMPT", model=settings.model,
                reasoning_effort=settings.reasoning_effort, cwd=DRIVER_ROOT,
                output_schema=DRIVER_ROOT / "contracts/job_analysis.schema.json",
            )
            self.assertFalse((Path(directory) / "logs").exists())
            self.assertEqual(result.errors, [])

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
        settings.config.set("proxy", "lean_context", "true")

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
        self.assert_lean_context_overrides(command)
        self.assertEqual(process_run.call_args.kwargs["input"], "PROMPT")
        self.assertEqual(command[command.index("--model") + 1], settings.model)
        self.assertEqual(
            cli_config_overrides(command)["model_reasoning_effort"],
            settings.reasoning_effort,
        )
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
        settings.config.set("proxy", "lean_context", "true")

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
        self.assert_lean_context_overrides(command)
        self.assertEqual(process_run.call_args.kwargs["input"], "NEXT INPUT")
        self.assertEqual(
            cli_config_overrides(command)["model_reasoning_effort"],
            settings.reasoning_effort,
        )
        self.assertEqual(result.thread_id, "thread-1")

    @patch("codex_proxy.codex_cli.shutil.which", return_value="codex")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_cli_lean_context_can_be_disabled_for_initial_and_resumed_calls(
        self, process_run, unused_which,
    ) -> None:
        process_run.return_value = SimpleNamespace(returncode=0, stdout="", stderr="")
        settings = load()
        settings.config.set("proxy", "lean_context", "false")

        for thread_id in (None, "thread-1"):
            with self.subTest(thread_id=thread_id):
                codex_cli.call(
                    config=settings.config,
                    prompt="BUSINESS PROMPT",
                    model=settings.model,
                    reasoning_effort=settings.reasoning_effort,
                    cwd=DRIVER_ROOT,
                    output_schema=DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
                    thread_id=thread_id,
                )
                command = process_run.call_args.args[0]
                self.assertEqual(cli_config_overrides(command), {
                    "model_reasoning_effort": settings.reasoning_effort,
                })
                self.assertEqual(process_run.call_args.kwargs["input"], "BUSINESS PROMPT")
                self.assertEqual("resume" in command, thread_id is not None)

    @patch("codex_proxy.codex_cli.shutil.which")
    @patch("codex_proxy.codex_cli.subprocess.run")
    def test_browser_mcp_starts_installed_script_without_npm(self, process_run, which) -> None:
        which.side_effect = lambda name: {"codex.cmd": "codex", "node": "node"}.get(name)
        process_run.return_value = SimpleNamespace(returncode=0, stdout="", stderr="")
        settings = load()
        settings.config.set("proxy", "lean_context", "true")
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            script = root / "node_modules/@playwright/mcp/cli.js"
            script.parent.mkdir(parents=True)
            script.write_text("", encoding="utf-8")
            with patch.object(codex_cli, "__file__", str(root / "codex_cli.py")):
                arguments = dict(
                    config=settings.config,
                    prompt="PROMPT",
                    model=settings.model,
                    reasoning_effort=settings.reasoning_effort,
                    cwd=DRIVER_ROOT,
                    output_schema=DRIVER_ROOT.parent / "Applier/result.schema.json",
                    browser_cdp_endpoint="http://127.0.0.1:9222",
                )
                for thread_id in (None, "browser-thread"):
                    with self.subTest(thread_id=thread_id):
                        codex_cli.call(**arguments, thread_id=thread_id)
                        command = process_run.call_args.args[0]
                        self.assert_lean_context_overrides(command)
                        server = cli_config_overrides(command)["mcp_servers.playwright"]
                        self.assertEqual(server["command"], "node")
                        self.assertEqual(server["args"], [str(script), "--cdp-endpoint", "http://127.0.0.1:9222"])
                        self.assertTrue(server["required"])
                        self.assertEqual(process_run.call_args.kwargs["input"], "PROMPT")
                self.assertNotIn("npx.cmd", [call.args[0] for call in which.call_args_list])
                script.unlink()
                with self.assertRaisesRegex(RuntimeError, "npm ci --prefix Driver/codex_proxy"):
                    codex_cli.call(**arguments)
                self.assertEqual(process_run.call_count, 2)

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

            save(
                operation="job_facts",
                target="1",
                duration_ms=1000,
                result=usage_result("job-thread", 100, 50, 10, 2),
                **common,
            )
            save(
                operation="job_facts",
                target="2",
                duration_ms=3000,
                result=usage_result("job-thread", 220, 130, 16, 3),
                **common,
            )
            save(
                operation="candidate_fit",
                target="1",
                duration_ms=2000,
                result=usage_result("fit-thread", 80, 20, 8, 1),
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
                invocations = connection.execute(
                    """
                    SELECT input_tokens, cached_input_tokens, output_tokens,
                           reasoning_output_tokens,
                           cumulative_input_tokens,
                           cumulative_cached_input_tokens,
                           cumulative_output_tokens,
                           cumulative_reasoning_output_tokens,
                           weighted_tokens, estimated_credits
                    FROM codex_invocations
                    WHERE run_id = ? AND operation = 'job_facts'
                    ORDER BY id
                    """,
                    ("run-1",),
                ).fetchall()
                totals = connection.execute(
                    """
                    SELECT sum(input_tokens), sum(cached_input_tokens),
                           sum(output_tokens), sum(reasoning_output_tokens),
                           sum(weighted_tokens), sum(estimated_credits)
                    FROM codex_invocations WHERE run_id = ?
                    """,
                    ("run-1",),
                ).fetchone()
                aggregate_costs = connection.execute(
                    """
                    SELECT weighted_tokens_sum, estimated_credits_sum
                    FROM codex_runs WHERE run_id = ?
                    """,
                    ("run-1",),
                ).fetchone()

            self.assertEqual(run, (300, 150, 24, 4))
            self.assertEqual([row[:8] for row in invocations], [
                (100, 50, 10, 2, 100, 50, 10, 2),
                (120, 80, 6, 1, 220, 130, 16, 3),
            ])
            for row, expected_weighted in zip(invocations, (115.0, 84.0)):
                self.assertAlmostEqual(row[8], expected_weighted)
                self.assertAlmostEqual(row[9], expected_weighted / 1_000_000)
            self.assertEqual(totals[:4], run)
            self.assertAlmostEqual(totals[4], 309.0)
            self.assertAlmostEqual(totals[5], 309.0 / 1_000_000)
            self.assertEqual(totals[4:], aggregate_costs)

    def test_usage_deltas_scope_sessions_and_handle_counter_resets(self) -> None:
        cases = [
            ("run-1", "shared-thread", "job_facts", (100, 50, 10, 2), (100, 50, 10, 2)),
            ("run-1", "shared-thread", "candidate_fit", (220, 130, 16, 3), (120, 80, 6, 1)),
            ("run-1", "other-thread", "job_facts", (80, 20, 8, 1), (80, 20, 8, 1)),
            ("run-2", "shared-thread", "job_facts", (30, 10, 3, 1), (30, 10, 3, 1)),
            ("run-1", "", "job_facts", (40, 10, 4, 1), (40, 10, 4, 1)),
            ("run-1", "", "job_facts", (70, 20, 5, 1), (70, 20, 5, 1)),
            ("run-1", "shared-thread", "job_facts", (30, 10, 3, 1), (30, 10, 3, 1)),
            ("run-1", "shared-thread", "job_facts", (50, 15, 5, 1), (20, 5, 2, 0)),
            ("run-1", "shared-thread", "job_facts", (0, 0, 0, 0), (0, 0, 0, 0)),
            ("run-1", "shared-thread", "job_facts", (70, 20, 7, 2), (20, 5, 2, 1)),
            ("run-1", "shared-thread", "job_facts", (90, 10, 8, 3), (90, 10, 8, 3)),
        ]
        with tempfile.TemporaryDirectory() as directory:
            db_path = Path(directory) / "jobs.sqlite"
            for index, (run_id, thread_id, operation, snapshot, _) in enumerate(cases):
                save(
                    db_path=db_path,
                    run_id=run_id,
                    operation=operation,
                    target=str(index),
                    model="gpt-test",
                    reasoning_effort="medium",
                    rates=(1.0, 0.1, 6.0),
                    started_at=f"2026-07-31T00:00:{index:02d}.000+00:00",
                    finished_at=f"2026-07-31T00:00:{index:02d}.500+00:00",
                    duration_ms=500,
                    result=usage_result(thread_id, *snapshot, exit_code=0 if any(snapshot) else 1),
                )
            with closing(sqlite3.connect(db_path)) as connection:
                rows = connection.execute(
                    """
                    SELECT input_tokens, cached_input_tokens, output_tokens,
                           reasoning_output_tokens
                    FROM codex_invocations ORDER BY id
                    """
                ).fetchall()
                runs = connection.execute(
                    """
                    SELECT run_id, input_tokens_sum, cached_input_tokens_sum,
                           output_tokens_sum, reasoning_output_tokens_sum,
                           failure_count
                    FROM codex_runs ORDER BY run_id
                    """
                ).fetchall()
                operation_inputs = connection.execute(
                    """
                    SELECT operation, input_tokens_sum
                    FROM codex_run_operations
                    WHERE run_id = 'run-1' ORDER BY operation
                    """
                ).fetchall()
        self.assertEqual(rows, [case[4] for case in cases])
        self.assertEqual(runs, [
            ("run-1", 570, 210, 48, 11, 1),
            ("run-2", 30, 10, 3, 1, 0),
        ])
        self.assertEqual(operation_inputs, [
            ("candidate_fit", 120),
            ("job_facts", 450),
        ])


if __name__ == "__main__":
    unittest.main()
