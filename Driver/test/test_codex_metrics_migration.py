from __future__ import annotations

from contextlib import closing
from pathlib import Path
import sqlite3
import unittest


MIGRATIONS = Path(__file__).resolve().parents[1] / "db" / "migrations"


class CodexMetricsMigrationTest(unittest.TestCase):
    def test_historical_snapshots_become_additive_without_changing_run_totals(self) -> None:
        with closing(sqlite3.connect(":memory:")) as connection:
            for name in (
                "007_codex_usage_metrics.sql",
                "008_codex_reasoning_effort.sql",
                "009_codex_run_operations.sql",
            ):
                connection.executescript((MIGRATIONS / name).read_text(encoding="utf-8"))
            connection.execute(
                """
                INSERT INTO codex_runs (run_id, started_at, finished_at)
                VALUES ('old-run', '2026-07-31T00:00:00', '2026-07-31T00:00:03')
                """
            )
            snapshots = [
                ("job_facts", "job-thread", (100, 50, 10, 2), (1.0, 0.1, 6.0)),
                ("job_facts", "job-thread", (220, 130, 16, 3), (2.0, 0.2, 10.0)),
                ("candidate_fit", "fit-thread", (80, 20, 8, 1), (0.5, 0.05, 3.0)),
            ]
            for index, (operation, thread_id, usage, rates) in enumerate(snapshots):
                input_tokens, cached_input_tokens, output_tokens, _ = usage
                input_rate, cached_rate, output_rate = rates
                snapshot_cost = (
                    (input_tokens - cached_input_tokens) * input_rate
                    + cached_input_tokens * cached_rate
                    + output_tokens * output_rate
                )
                connection.execute(
                    """
                    INSERT INTO codex_invocations (
                        run_id, operation, command, model, thread_id,
                        started_at, finished_at, duration_ms, exit_code, status,
                        input_tokens, cached_input_tokens, output_tokens,
                        reasoning_output_tokens, input_credit_rate,
                        cached_input_credit_rate, output_credit_rate,
                        weighted_tokens, estimated_credits
                    ) VALUES ('old-run', ?, 'codex exec', 'historical-model', ?,
                              ?, ?, 1000, 0, 'success', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        operation,
                        thread_id,
                        f"2026-07-31T00:00:0{index}",
                        f"2026-07-31T00:00:0{index + 1}",
                        *usage,
                        *rates,
                        snapshot_cost / input_rate,
                        snapshot_cost / 1_000_000,
                    ),
                )

            connection.executescript(
                (MIGRATIONS / "035_fix_persistent_usage_aggregates.sql").read_text(encoding="utf-8")
            )
            old_runs = connection.execute("SELECT * FROM codex_runs ORDER BY run_id").fetchall()
            old_operations = connection.execute(
                "SELECT * FROM codex_run_operations ORDER BY run_id, operation"
            ).fetchall()

            connection.executescript(
                (MIGRATIONS / "040_codex_invocation_usage_deltas.sql").read_text(encoding="utf-8")
            )

            self.assertEqual(
                connection.execute("SELECT * FROM codex_runs ORDER BY run_id").fetchall(),
                old_runs,
            )
            self.assertEqual(
                connection.execute(
                    "SELECT * FROM codex_run_operations ORDER BY run_id, operation"
                ).fetchall(),
                old_operations,
            )
            rows = connection.execute(
                """
                SELECT input_tokens, cached_input_tokens, output_tokens,
                       reasoning_output_tokens,
                       cumulative_input_tokens, cumulative_cached_input_tokens,
                       cumulative_output_tokens,
                       cumulative_reasoning_output_tokens,
                       input_credit_rate, cached_input_credit_rate, output_credit_rate,
                       weighted_tokens, estimated_credits
                FROM codex_invocations ORDER BY id
                """
            ).fetchall()
            self.assertEqual([row[:8] for row in rows], [
                (100, 50, 10, 2, 100, 50, 10, 2),
                (120, 80, 6, 1, 220, 130, 16, 3),
                (80, 20, 8, 1, 80, 20, 8, 1),
            ])
            self.assertEqual([row[8:11] for row in rows], [item[3] for item in snapshots])
            for row, weighted, cost in zip(rows, (115.0, 78.0, 110.0), (115.0, 156.0, 55.0)):
                self.assertAlmostEqual(row[11], weighted)
                self.assertAlmostEqual(row[12], cost / 1_000_000)
            totals = connection.execute(
                """
                SELECT sum(input_tokens), sum(cached_input_tokens),
                       sum(output_tokens), sum(reasoning_output_tokens),
                       sum(weighted_tokens), sum(estimated_credits)
                FROM codex_invocations
                """
            ).fetchone()
            self.assertEqual(totals[:4], (300, 150, 24, 4))
            self.assertAlmostEqual(totals[4], 303.0)
            self.assertAlmostEqual(totals[5], 326.0 / 1_000_000)


if __name__ == "__main__":
    unittest.main()
