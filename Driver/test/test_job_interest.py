from __future__ import annotations

import sys
from pathlib import Path
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.job_interest.calculate import load_config, relocation_score, remote_score


class JobInterestTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.config = load_config(
            DRIVER_ROOT / "analyzer" / "job_interest" / "config" / "interest.ini"
        )

    def test_unknown_remote_scope_gets_half_minimum_location_score(self) -> None:
        self.assertEqual(remote_score(self.config, "remote", "unknown"), 200)

    def test_known_remote_scope_uses_resume_score(self) -> None:
        self.assertEqual(remote_score(self.config, "remote", "Moldova"), 800)

    def test_unavailable_relocation_has_no_bonus(self) -> None:
        self.assertEqual(relocation_score(self.config, "United States"), 0)

    def test_available_relocation_uses_resume_score_and_base(self) -> None:
        self.assertEqual(relocation_score(self.config, "Poland"), 600)


if __name__ == "__main__":
    unittest.main()
