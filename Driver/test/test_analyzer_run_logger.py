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

from analyzer.run_logger import start_analysis_run


class AnalyzerRunLoggerTest(unittest.TestCase):
    def test_persists_effective_values_and_explicit_overrides(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            run_id = "20260918T120000Z-batch-linkedin"

            start_analysis_run(run_id, 6, 5, database)
            start_analysis_run(run_id, 3, 2, database)

            with closing(sqlite3.connect(database)) as connection:
                row = connection.execute(
                    """
                    SELECT parallel_agents, vacancies_per_agent
                    FROM analyzer_runs
                    WHERE run_id = ?
                    """,
                    (run_id,),
                ).fetchone()

            self.assertEqual(row, (3, 2))

    def test_rejects_nonpositive_settings(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            with self.assertRaisesRegex(ValueError, "parallel_agents"):
                start_analysis_run("run-1", 0, 5, database)
            with self.assertRaisesRegex(ValueError, "vacancies_per_agent"):
                start_analysis_run("run-1", 6, 0, database)


if __name__ == "__main__":
    unittest.main()
