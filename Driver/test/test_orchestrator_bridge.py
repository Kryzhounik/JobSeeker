from __future__ import annotations

from contextlib import closing
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


class OrchestratorBridgeTest(unittest.TestCase):
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

    def test_mark_nonrelevant_updates_all_ids_in_one_database(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"

            python_bridge.mark_nonrelevant(
                str(database),
                "linkedin",
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
