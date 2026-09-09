from __future__ import annotations

import copy
import json
from pathlib import Path
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from contracts.validate_json import SchemaValidationError, validate_json
from analyzer.candidate_fit.add_fit_score import add_fit_score


ANALYSIS_SCHEMA = DRIVER_ROOT / "contracts" / "job_analysis.schema.json"
FIT_SCHEMA = DRIVER_ROOT / "contracts" / "candidate_fit_result.schema.json"
TITLE_FILTER_SCHEMA = DRIVER_ROOT / "contracts" / "title_filter_result.schema.json"


def valid_analysis() -> dict:
    return {
        "source": "linkedin",
        "added_at": "2026-09-08",
        "source_url": "https://www.linkedin.com/jobs/view/1/",
        "title": "Java Developer",
        "company": "Example",
        "location": "Moldova",
        "remote_type": "remote",
        "remote_scope": "Moldova",
        "relocation": "",
        "seniority": "senior",
        "role": "backend",
        "salary": "",
        "summary": "Backend role",
        "notes": "",
        "languages": [
            {"name": "English", "level": "B2", "level_rank": 4, "raw_value": "B2"}
        ],
        "technologies": [
            {
                "name": "Java",
                "requirement": "core",
                "level": "advanced",
                "level_rank": 4,
                "raw_value": "advanced Java",
            }
        ],
    }


class JsonValidationTest(unittest.TestCase):
    def test_accepts_valid_analysis(self) -> None:
        validate_json(valid_analysis(), ANALYSIS_SCHEMA)

    def test_rejects_missing_analysis_field(self) -> None:
        value = valid_analysis()
        del value["title"]
        with self.assertRaises(SchemaValidationError):
            validate_json(value, ANALYSIS_SCHEMA)

    def test_rejects_extra_analysis_field(self) -> None:
        value = valid_analysis()
        value["candidate_fit_percent"] = 70
        with self.assertRaises(SchemaValidationError):
            validate_json(value, ANALYSIS_SCHEMA)

    def test_rejects_invalid_nested_analysis_value(self) -> None:
        value = copy.deepcopy(valid_analysis())
        value["technologies"][0]["level_rank"] = 8
        with self.assertRaises(SchemaValidationError):
            validate_json(value, ANALYSIS_SCHEMA)

    def test_validates_candidate_fit_result(self) -> None:
        validate_json(
            {
                "candidate_fit_percent": 66,
                "candidate_fit_reason_code": "ok",
                "candidate_fit_reason": "Good fit",
            },
            FIT_SCHEMA,
        )
        with self.assertRaises(SchemaValidationError):
            validate_json(
                {
                    "candidate_fit_percent": 101,
                    "candidate_fit_reason_code": "ok",
                    "candidate_fit_reason": "Invalid score",
                },
                FIT_SCHEMA,
            )

    def test_add_fit_score_rejects_invalid_result_before_writing(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            source_path = Path(directory) / "analyzed.json"
            output_path = Path(directory) / "scored.json"
            source_path.write_text(
                json.dumps(valid_analysis(), ensure_ascii=False),
                encoding="utf-8",
            )

            with self.assertRaises(SchemaValidationError):
                add_fit_score(
                    source_path,
                    {
                        "candidate_fit_percent": 101,
                        "candidate_fit_reason_code": "ok",
                        "candidate_fit_reason": "Invalid score",
                    },
                    output_path,
                )

            self.assertFalse(output_path.exists())

    def test_validates_title_filter_result(self) -> None:
        validate_json(
            {"nonrelevant_job_ids": ["123", "456"]},
            TITLE_FILTER_SCHEMA,
        )
        with self.assertRaises(SchemaValidationError):
            validate_json(
                {"nonrelevant_job_ids": [""]},
                TITLE_FILTER_SCHEMA,
            )


if __name__ == "__main__":
    unittest.main()
