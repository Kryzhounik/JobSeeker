from __future__ import annotations

import csv
import json
from pathlib import Path
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.evals.facts_report import compare_facts, save_facts_report
from analyzer.evals.report import save_report


class AnalyzerEvalFactsReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.expected = self.root / "expected"
        self.analyzed = self.root / "analyzed"
        self._write(self.expected, "linkedin", "job-1", {
            "location": "Ukraine",
            "languages": [
                {"name": "English", "level": "B2", "level_rank": 4},
                {"name": "French", "level": "A2", "level_rank": 2},
            ],
        })
        self._write(self.analyzed, "linkedin", "job-1", {
            "location": "Ukraine",
            "languages": [
                {"name": "French", "level": "A2", "level_rank": 2},
                {"name": "English", "level": "B2", "level_rank": 4, "raw_value": "English B2"},
            ],
            "unreviewed_actual_field": "ignored",
        })

    @staticmethod
    def _write(root: Path, source: str, job_id: str, value: dict) -> None:
        path = root / source / f"{job_id}.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(value), encoding="utf-8")

    def test_compares_expected_fields_and_ignores_language_order_and_extra_actuals(self) -> None:
        report = compare_facts(self.expected, self.analyzed)

        self.assertEqual(report["total_fields"], 2)
        self.assertEqual(report["matched_fields"], 2)
        self.assertTrue(report["complete"])
        vacancy, = report["vacancies"]
        self.assertEqual((vacancy["source"], vacancy["job_id"]), ("linkedin", "job-1"))
        self.assertTrue(vacancy["complete"])
        self.assertEqual(set(vacancy["fields"]), {"location", "languages"})
        self.assertTrue(vacancy["fields"]["languages"]["match"])

    def test_reports_actual_mismatch_without_interpreting_language_alternatives(self) -> None:
        self._write(self.analyzed, "linkedin", "job-1", {
            "location": "Ukraine",
            "languages": [{"name": "English", "level": "C1", "level_rank": 5}],
        })

        report = compare_facts(self.expected, self.analyzed)

        self.assertTrue(report["complete"])
        self.assertEqual(report["matched_fields"], 1)
        languages = report["vacancies"][0]["fields"]["languages"]
        self.assertFalse(languages["match"])
        self.assertEqual(languages["expected"][0]["level"], "B2")
        self.assertEqual(languages["actual"][0]["level"], "C1")

    def test_missing_actual_is_incomplete_and_expected_fields_are_written(self) -> None:
        (self.analyzed / "linkedin" / "job-1.json").unlink()
        report = compare_facts(self.expected, self.analyzed)

        self.assertFalse(report["complete"])
        self.assertFalse(report["vacancies"][0]["complete"])
        self.assertEqual(report["matched_fields"], 0)
        self.assertEqual(report["total_fields"], 2)
        self.assertTrue(all(not field["match"] for field in report["vacancies"][0]["fields"].values()))

    def test_saves_facts_json(self) -> None:
        report = compare_facts(self.expected, self.analyzed)
        result_dir = self.root / "result"

        save_facts_report(result_dir, report)

        self.assertEqual(json.loads((result_dir / "facts-report.json").read_text(encoding="utf-8")), report)

    def test_combined_report_csv_contains_scores_fact_statistics_and_mismatches(self) -> None:
        self._write(self.analyzed, "linkedin", "job-1", {
            "location": "Moldova",
            "languages": [
                {"name": "English", "level": "C1", "level_rank": 5, "raw_value": "English C1"},
            ],
            "company": "Fixture Company",
            "title": "Fixture Engineer",
        })
        facts = compare_facts(self.expected, self.analyzed)
        scores = {
            "complete": True,
            "metrics": {"asymmetric_mse": 0, "mse": 0, "bias": 0},
            "vacancies": [{
                "source": "linkedin", "job_id": "job-1", "company": "Fixture Company",
                "title": "Fixture Engineer", "expected": 75, "actual": 70,
                "error": -5, "asymmetric_loss": 75, "status": "SAVED",
            }],
        }
        result_dir = self.root / "combined-result"
        result_dir.mkdir()

        save_report(result_dir, scores, facts)

        with (result_dir / "report.csv").open(encoding="utf-8-sig", newline="") as file:
            rows = list(csv.reader(file))
        self.assertIn(["linkedin", "job-1", "Fixture Company", "Fixture Engineer", "75", "70", "-5", "75", "SAVED"], rows)
        self.assertIn(["Fields compared", "2"], rows)
        self.assertIn(["Fields matching", "0"], rows)
        self.assertIn(["Fields differing", "2"], rows)
        mismatch_header = ["source", "job_id", "company", "title", "field", "expected", "actual", "present", "match"]
        mismatch_start = rows.index(mismatch_header)
        mismatch_rows = rows[mismatch_start + 1:]
        self.assertEqual({row[4] for row in mismatch_rows}, {"location", "languages"})
        language_row = next(row for row in mismatch_rows if row[4] == "languages")
        self.assertNotIn("raw_value", language_row[6])
        self.assertTrue((result_dir / "report.json").is_file())
        self.assertFalse((result_dir / "facts-report.csv").exists())


if __name__ == "__main__":
    unittest.main()
