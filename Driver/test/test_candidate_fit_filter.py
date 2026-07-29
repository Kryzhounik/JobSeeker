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

    def test_worldwide_candidate_location_does_not_allow_any_onsite_location(self) -> None:
        result = filter_job_json(base_job(location="New York, United States"))
        self.assertFalse(result.passed)
        self.assertEqual(result.reason_code, "loc")

if __name__ == "__main__":
    unittest.main()
