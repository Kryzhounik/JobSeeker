from __future__ import annotations

from pathlib import Path
import sys
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.extract_linkedin_readable_text_v3 import repair_mojibake


class RepairMojibakeTests(unittest.TestCase):
    def test_repairs_linkedin_punctuation(self) -> None:
        damaged = "Ukraine В· Remote вЂў Rate: в‚¬500 вЂ“ в‚¬650 в†’ apply"
        expected = "Ukraine · Remote • Rate: €500 – €650 → apply"
        self.assertEqual(repair_mojibake(damaged), expected)

    def test_preserves_valid_unicode(self) -> None:
        valid = "Українська обов’язкова · €500–€650 → apply 🚀"
        self.assertEqual(repair_mojibake(valid), valid)


if __name__ == "__main__":
    unittest.main()
