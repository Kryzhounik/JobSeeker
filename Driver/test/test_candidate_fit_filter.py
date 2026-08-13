from __future__ import annotations

import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.candidate_fit.filter import filter_job_json


def base_job(**overrides):
    job = {
        "title": "Senior Java Software Engineer",
        "languages": [{"name": "English", "level": "B2", "level_rank": 4, "raw_value": "English B2"}],
        "technologies": [{"name": "Java", "requirement": "required", "level": "regular", "level_rank": 3, "raw_value": "Java"}],
        "location": "",
        "remote_type": "hybrid",
        "remote_scope": "",
        "relocation": "NO",
    }
    job.update(overrides)
    return job


class CandidateFitFilterTest(unittest.TestCase):
    def test_hybrid_allowed_onsite_location_passes(self) -> None:
        result = filter_job_json(base_job(location="Chisinau, Moldova"))
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_unknown_work_type_at_allowed_location_passes(self) -> None:
        result = filter_job_json(
            base_job(
                location="Greater Drohobych Area, Ukraine",
                remote_type="unknown",
            )
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_hybrid_disallowed_onsite_location_fails_location(self) -> None:
        result = filter_job_json(base_job(location="Warsaw, Poland"))
        self.assertFalse(result.passed)
        self.assertEqual(result.reason_code, "loc")

    def test_remote_worldwide_scope_allows_available_location(self) -> None:
        result = filter_job_json(
            base_job(remote_type="remote", remote_scope="worldwide", location="")
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_remote_region_scope_allows_available_location(self) -> None:
        result = filter_job_json(
            base_job(remote_type="remote", remote_scope="EMEA", location="")
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_remote_country_outside_available_locations_fails_location(self) -> None:
        result = filter_job_json(
            base_job(remote_type="remote", remote_scope="Poland", location="")
        )
        self.assertFalse(result.passed)
        self.assertEqual(result.reason_code, "loc")

    def test_unknown_remote_scope_does_not_hard_reject(self) -> None:
        result = filter_job_json(
            base_job(remote_type="remote", remote_scope="unknown", location="Lithuania")
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_worldwide_candidate_location_does_not_allow_any_onsite_location(self) -> None:
        result = filter_job_json(base_job(location="New York, United States"))
        self.assertFalse(result.passed)
        self.assertEqual(result.reason_code, "loc")

    def test_required_missing_programming_language_does_not_hard_fail(self) -> None:
        result = filter_job_json(
            base_job(
                technologies=[
                    {
                        "name": "Go",
                        "requirement": "required",
                        "level": "advanced",
                        "level_rank": 4,
                        "raw_value": "Strong Go experience",
                    }
                ],
                remote_type="remote",
                remote_scope="worldwide",
            )
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_core_missing_programming_language_fails_tech(self) -> None:
        result = filter_job_json(
            base_job(
                technologies=[
                    {
                        "name": "C++",
                        "requirement": "core",
                        "level": "regular",
                        "level_rank": 3,
                        "raw_value": "2+ years of C++ experience",
                    }
                ],
                remote_type="remote",
                remote_scope="worldwide",
            )
        )
        self.assertFalse(result.passed)
        self.assertEqual(result.reason_code, "tech")
        self.assertIn("core programming language missing", result.reason)

    def test_basic_core_programming_language_does_not_hard_fail(self) -> None:
        result = filter_job_json(
            base_job(
                technologies=[
                    {
                        "name": "Python",
                        "requirement": "core",
                        "level": "junior",
                        "level_rank": 2,
                        "raw_value": "Familiarity with Python; analogous experience accepted",
                    }
                ],
                remote_type="remote",
                remote_scope="worldwide",
            )
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_core_programming_language_alternatives_use_known_option(self) -> None:
        result = filter_job_json(
            base_job(
                technologies=[
                    {
                        "name": "Python / Java / Go",
                        "requirement": "core",
                        "level": "regular",
                        "level_rank": 3,
                        "raw_value": "Experience with Python, Java, or Go",
                    }
                ],
                remote_type="remote",
                remote_scope="worldwide",
            )
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

    def test_core_mixed_alternative_item_does_not_hard_fail(self) -> None:
        result = filter_job_json(
            base_job(
                technologies=[
                    {
                        "name": "Python / Scala / SQL",
                        "requirement": "core",
                        "level": "regular",
                        "level_rank": 3,
                        "raw_value": "Experience with SQL, Python, or Scala",
                    }
                ],
                remote_type="remote",
                remote_scope="worldwide",
            )
        )
        self.assertTrue(result.passed)
        self.assertEqual(result.reason_code, "ok")

if __name__ == "__main__":
    unittest.main()
