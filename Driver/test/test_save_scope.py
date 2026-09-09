from __future__ import annotations

from pathlib import Path
import sys
import tempfile
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.save import json_paths


class SaveScopeTest(unittest.TestCase):
    def test_selects_requested_json_files_in_order(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("1.json", "2.json", "other.json"):
                (root / name).write_text("{}", encoding="utf-8")

            self.assertEqual(
                json_paths(root, ["2", "1"]),
                [root / "2.json", root / "1.json"],
            )

    def test_rejects_missing_requested_file(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "missing scored JSON files"):
                json_paths(Path(directory), ["missing"])


if __name__ == "__main__":
    unittest.main()
