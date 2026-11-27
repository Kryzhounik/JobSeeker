from __future__ import annotations

import csv
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.evals.report import build_report, save_report


class AnalyzerEvalReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.result_dir = Path(self.temp.name)
        (self.result_dir / "Data").mkdir()
        self._create_database()

    def _create_database(self, saved: tuple[tuple[str, float], ...] | None = None) -> None:
        if saved is None:
            saved = (("job-1", 40), ("job-2", 80), ("job-3", 60))
        database = self.result_dir / "Data" / "jobs.sqlite"
        with closing(sqlite3.connect(database)) as connection, connection:
            connection.executescript(
                """
                CREATE TABLE source_jobs (
                    id INTEGER PRIMARY KEY,
                    source TEXT NOT NULL,
                    source_job_id TEXT NOT NULL,
                    processing_status TEXT NOT NULL
                );
                CREATE TABLE jobs (
                    source_job_ref INTEGER NOT NULL,
                    candidate_fit_percent REAL
                );
                CREATE TABLE companies (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL
                );
                CREATE TABLE source_job_texts (
                    source_job_ref INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    company_id INTEGER
                );
                """
            )
            for index, (job_id, actual) in enumerate(saved, start=1):
                connection.execute(
                    "INSERT INTO source_jobs VALUES (?, 'linkedin', ?, 'SAVED')",
                    (index, job_id),
                )
                connection.execute("INSERT INTO jobs VALUES (?, ?)", (index, actual))
                company = "Café, Inc." if index == 1 else f"Company {index}"
                title = "Développeur, senior" if index == 1 else f"Engineer {index}"
                connection.execute("INSERT INTO companies VALUES (?, ?)", (index, company))
                connection.execute(
                    "INSERT INTO source_job_texts VALUES (?, ?, ?)",
                    (index, title, index),
                )

    def _write_inputs(
        self,
        job_ids: tuple[str, ...] = ("job-1", "job-2", "job-3"),
        references: tuple[tuple[str, int, int], ...] | None = None,
        source: str = "linkedin",
    ) -> None:
        (self.result_dir / "metadata.json").write_text(
            json.dumps({"source": source, "job_ids": list(job_ids)}),
            encoding="utf-8",
        )
        if references is None:
            references = (("job-1", 50, 50), ("job-2", 70, 70), ("job-3", 60, 60))
        with (self.result_dir / "external-scores.csv").open(
            "w", encoding="utf-8", newline=""
        ) as file:
            writer = csv.writer(file)
            writer.writerow(("source", "job_id", "minimum_percent", "maximum_percent"))
            writer.writerows((source, *row) for row in references)

    def test_calculates_asymmetric_mse_mse_bias_and_rows(self) -> None:
        self._write_inputs()

        report = build_report(self.result_dir, 3)

        self.assertTrue(report["complete"])
        self.assertAlmostEqual(report["metrics"]["asymmetric_mse"], 400 / 3)
        self.assertAlmostEqual(report["metrics"]["mse"], 200 / 3)
        self.assertAlmostEqual(report["metrics"]["bias"], 0)
        self.assertEqual([row["error"] for row in report["vacancies"]], [-10, 10, 0])
        self.assertEqual([row["asymmetric_loss"] for row in report["vacancies"]], [300, 100, 0])

    def test_missing_saved_job_makes_report_incomplete_and_metrics_null(self) -> None:
        with closing(sqlite3.connect(self.result_dir / "Data" / "jobs.sqlite")) as connection, connection:
            connection.execute("DELETE FROM jobs WHERE source_job_ref = 2")
        self._write_inputs()

        report = build_report(self.result_dir, 3)

        self.assertFalse(report["complete"])
        self.assertEqual(
            report["metrics"],
            {"asymmetric_mse": None, "mse": None, "bias": None},
        )
        missing = next(row for row in report["vacancies"] if row["job_id"] == "job-2")
        self.assertIsNone(missing["actual"])

    def test_mismatched_scope_and_reference_each_make_metrics_null(self) -> None:
        cases = (
            (("job-1", "job-2"), None),
            (("job-1", "job-2", "job-3"), (("job-1", 50, 50), ("job-2", 70, 70))),
        )
        for job_ids, references in cases:
            with self.subTest(job_ids=job_ids, references=references):
                self._write_inputs(job_ids=job_ids, references=references)

                report = build_report(self.result_dir, 3)

                self.assertFalse(report["complete"])
                self.assertTrue(
                    all(value is None for value in report["metrics"].values())
                )

    def test_csv_contains_utf8_bom_metadata_and_scores(self) -> None:
        with closing(sqlite3.connect(self.result_dir / "Data" / "jobs.sqlite")) as connection, connection:
            connection.execute("DELETE FROM jobs WHERE source_job_ref = 2")
        self._write_inputs()
        report = build_report(self.result_dir, 3)

        save_report(self.result_dir, report)

        csv_path = self.result_dir / "report.csv"
        self.assertTrue(csv_path.read_bytes().startswith(b"\xef\xbb\xbf"))
        with csv_path.open(encoding="utf-8-sig", newline="") as file:
            reader = csv.DictReader(file)
            self.assertEqual(
                reader.fieldnames,
                ["source", "job_id", "company", "title", "expected", "actual",
                 "error", "asymmetric_loss", "status"],
            )
            rows = list(reader)

        first = rows[0]
        self.assertEqual(first["company"], "Café, Inc.")
        self.assertEqual(first["title"], "Développeur, senior")
        self.assertEqual(first["expected"], "50.0")
        self.assertEqual(first["actual"], "40.0")
        self.assertEqual(first["error"], "-10.0")
        self.assertEqual(first["asymmetric_loss"], "300.0")
        missing = rows[1]
        self.assertEqual(missing["company"], "Company 2")
        self.assertEqual(missing["expected"], "70.0")
        self.assertEqual(missing["actual"], "")
        self.assertEqual(missing["error"], "")
        self.assertEqual(missing["asymmetric_loss"], "")
        self.assertEqual(missing["status"], "SAVED")
        self.assertTrue((self.result_dir / "report.json").exists())


if __name__ == "__main__":
    unittest.main()
