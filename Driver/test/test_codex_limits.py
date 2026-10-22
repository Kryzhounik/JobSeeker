from datetime import datetime, timedelta, timezone
import io
import json
from pathlib import Path
import sys
from types import SimpleNamespace
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.backend import Result
from codex_proxy import bridge, metrics_proxy
from codex_proxy.errors import UsageLimitExceededError, usage_limit_error


class CodexLimitsTest(unittest.TestCase):
    def test_recognizes_actual_cli_messages_and_next_day_reset(self):
        zone = timezone(timedelta(hours=3))
        now = datetime(2026, 10, 22, 13, 27, tzinfo=zone)
        for apostrophe in ("'", "\u2019"):
            message = f"You{apostrophe}ve hit your usage limit. Upgrade or try again at 3:20 PM."
            events = [json.dumps({"type": "turn.failed", "error": {"message": message}})]
            error = usage_limit_error(events, now)
            self.assertIsInstance(error, UsageLimitExceededError)
            self.assertEqual(error.retry_at, datetime(2026, 10, 22, 15, 20, tzinfo=zone))
            self.assertEqual(error.payload()["retry_at"], "2026-10-22T12:20:00+00:00")
            self.assertEqual(usage_limit_error(events, now.replace(hour=23)).retry_at.day, 23)

    def test_prefers_structured_reset_and_keeps_unknown_time_unset(self):
        now = datetime(2026, 10, 22, tzinfo=timezone.utc)
        error = usage_limit_error([json.dumps({"error": {
            "code": "usage_limit_reached", "message": "Quota exhausted", "resets_at": 1792671600,
        }})], now)
        self.assertEqual(error.retry_at, datetime.fromtimestamp(1792671600, timezone.utc))
        error = usage_limit_error([json.dumps({"message": "You've hit your usage limit."})], now)
        self.assertIsNone(error.retry_at)
        for message in ("Selected model is at capacity", "HTTP 429", "Invalid model"):
            self.assertIsNone(usage_limit_error([json.dumps({"message": message})], now))

    def test_bridge_translates_only_the_limit(self):
        limit = UsageLimitExceededError("Quota exhausted", datetime(2026, 10, 22, tzinfo=timezone.utc))
        def limited():
            raise limit
        with patch.object(bridge, "import_module", return_value=SimpleNamespace(operation=limited)):
            response = json.loads(bridge.call("operation_module", "operation", "[]"))
        self.assertEqual(response, {"error": limit.payload()})
        with patch.object(bridge, "import_module", return_value=SimpleNamespace(operation=lambda value: value)):
            self.assertEqual(json.loads(bridge.call("operation_module", "operation", '["thread-1"]')),
                             {"result": "thread-1"})

    @patch("codex_proxy.metrics_proxy.metrics.save")
    @patch("codex_proxy.metrics_proxy.cli_prompt", return_value="PROMPT")
    @patch("codex_proxy.metrics_proxy.codex_cli.call")
    def test_metered_call_records_usage_before_raising_limit(self, invoke, unused_prompt, save):
        invoke.return_value = Result(["codex", "exec"], 1, "thread-1", "", {
            "input_tokens": 0, "cached_input_tokens": 0, "output_tokens": 0, "reasoning_output_tokens": 0,
        }, [json.dumps({"type": "error", "message": "You've hit your usage limit. Try again at 3:20 PM."})])
        with self.assertRaises(UsageLimitExceededError):
            metrics_proxy.run(run_id="run-1", operation="job_facts", target="1",
                              instruction_path=Path("instruction.md"), input_text="{}", input_name="1.json",
                              context_paths=(), output_schema=Path("schema.json"))
        save.assert_called_once()
        self.assertEqual(save.call_args.kwargs["result"].exit_code, 1)

    @patch("codex_proxy.metrics_proxy.run_from_args")
    def test_process_boundary_preserves_unicode_payload_in_ascii_json(self, run):
        payload = '{"title":"\u0420\u0430\u0437\u0440\u0430\u0431\u043e\u0442\u0447\u0438\u043a"}'
        run.return_value = Result([], 0, "thread-1", payload, {}, [])
        argv = ["metrics_proxy", "--run-id", "run-1", "--operation", "job_facts",
                "--instruction", "instruction.md", "--input", "-", "--output-schema", "schema.json", "--json-result"]
        with patch.object(sys, "argv", argv), patch.object(sys, "stdin", io.StringIO("{}")), \
                patch.object(sys, "stdout", io.StringIO()) as output:
            with self.assertRaises(SystemExit) as stopped:
                metrics_proxy.main()
        self.assertEqual(stopped.exception.code, 0)
        self.assertTrue(output.getvalue().isascii())
        self.assertEqual(json.loads(output.getvalue()), {"result": payload})

    @patch("codex_proxy.metrics_proxy.run_from_args")
    def test_process_boundary_emits_the_same_structured_limit(self, run):
        limit = UsageLimitExceededError("Quota exhausted", datetime(2026, 10, 22, tzinfo=timezone.utc))
        run.side_effect = limit
        argv = ["metrics_proxy", "--run-id", "run-1", "--operation", "agent_filter",
                "--instruction", "instruction.md", "--input", "-", "--output-schema", "schema.json", "--json-result"]
        with patch.object(sys, "argv", argv), patch.object(sys, "stdin", io.StringIO("[]")), \
                patch.object(sys, "stdout", io.StringIO()) as output:
            with self.assertRaises(SystemExit) as stopped:
                metrics_proxy.main()
        self.assertEqual(stopped.exception.code, 1)
        self.assertEqual(json.loads(output.getvalue()), {"error": limit.payload()})


if __name__ == "__main__":
    unittest.main()
