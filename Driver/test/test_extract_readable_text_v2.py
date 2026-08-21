from __future__ import annotations

import importlib.util
from pathlib import Path
import sys
import unittest


COLLECTOR_DIR = Path(__file__).resolve().parents[1] / "collector"
sys.path.insert(0, str(COLLECTOR_DIR))
SPEC = importlib.util.spec_from_file_location(
    "extract_linkedin_readable_text_v2",
    COLLECTOR_DIR / "extract_linkedin_readable_text_v2.py",
)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class RepairMojibakeTests(unittest.TestCase):
    def test_repairs_linkedin_punctuation(self) -> None:
        damaged = "Ukraine В· Remote вЂў Rate: в‚¬500 вЂ“ в‚¬650 в†’ apply"
        expected = "Ukraine · Remote • Rate: €500 – €650 → apply"
        self.assertEqual(MODULE.repair_mojibake(damaged), expected)

    def test_preserves_valid_unicode(self) -> None:
        valid = "Українська обов’язкова · €500–€650 → apply 🚀"
        self.assertEqual(MODULE.repair_mojibake(valid), valid)

    def test_normalize_repairs_before_filtering(self) -> None:
        self.assertEqual(MODULE.normalize_text("Role В· Remote\nвЂў Java"), "Role · Remote\n• Java\n")

    def test_removes_guest_sign_in_block(self) -> None:
        text = """Senior Java Developer
Example Corp
Ukraine
Join or sign in to find your next job
Email
Password
Sign in
Description
Build Java services.
"""

        self.assertEqual(
            MODULE.normalize_text(text),
            "Senior Java Developer\nExample Corp\nUkraine\nDescription\nBuild Java services.\n",
        )

    def test_removes_guest_metadata_tail(self) -> None:
        text = "Description\nBuild Java services.\nShow more\nSeniority level\nSenior\n"

        self.assertEqual(
            MODULE.normalize_text(text),
            "Description\nBuild Java services.\n",
        )


if __name__ == "__main__":
    unittest.main()
