from __future__ import annotations

from contextlib import closing
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.backend import Result
from codex_proxy.metrics_proxy import cli_prompt
from codex_proxy.metrics import save
from codex_proxy.output_schema import codex_schema


class CodexProxyTest(unittest.TestCase):
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
            input_path = root / "input.json"
            context = root / "context.ini"
            instruction.write_text("FIT RULES", encoding="utf-8")
            input_path.write_text('{"title":"Backend"}', encoding="utf-8")
            context.write_text("CANDIDATE PROFILE", encoding="utf-8")

            prompt = cli_prompt(instruction, input_path, (context,))

            self.assertIn("FIT RULES", prompt)
            self.assertIn('{"title":"Backend"}', prompt)
            self.assertIn("CANDIDATE PROFILE", prompt)

    def test_metrics_are_aggregated_by_operation_inside_run(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            db_path = Path(directory) / "jobs.sqlite"
            common = dict(
                db_path=db_path,
                run_id="run-1",
                model="gpt-test",
                reasoning_effort="medium",
                rates=(1.0, 0.1, 6.0),
                started_at="2026-07-31T00:00:00.000+00:00",
                finished_at="2026-07-31T00:00:01.000+00:00",
                result=Result(
                    ["codex", "exec"],
                    0,
                    "thread",
                    "{}",
                    {
                        "input_tokens": 100,
                        "cached_input_tokens": 50,
                        "output_tokens": 10,
                        "reasoning_output_tokens": 2,
                    },
                    [],
                ),
            )
            save(operation="job_facts", target="1", duration_ms=1000, **common)
            save(operation="job_facts", target="2", duration_ms=3000, **common)
            save(operation="candidate_fit", target="1", duration_ms=2000, **common)

            with closing(sqlite3.connect(db_path)) as connection:
                rows = connection.execute(
                    """
                    SELECT operation, invocation_count, duration_ms_sum,
                           input_tokens_sum, input_tokens_avg
                    FROM codex_run_operations
                    WHERE run_id = ?
                    ORDER BY operation
                    """,
                    ("run-1",),
                ).fetchall()

            self.assertEqual(rows, [
                ("candidate_fit", 1, 2000, 100, 100.0),
                ("job_facts", 2, 4000, 200, 100.0),
            ])


if __name__ == "__main__":
    unittest.main()
