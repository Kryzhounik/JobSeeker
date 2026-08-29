from __future__ import annotations

from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from GUI.jobs_viewer import append_unique_blacklist_term


class TitleBlacklistTest(unittest.TestCase):
    def test_appends_normalized_term_and_preserves_existing_content(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "blocked.txt"
            path.write_text("# Comment\nJava Developer", encoding="utf-8")

            added = append_unique_blacklist_term(
                path,
                "  Python   Automation  ",
            )

            self.assertTrue(added)
            self.assertEqual(
                path.read_text(encoding="utf-8"),
                "# Comment\nJava Developer\nPython Automation\n",
            )

    def test_does_not_add_case_insensitive_duplicate(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "blocked.txt"
            path.write_text("Python Automation\n", encoding="utf-8")

            added = append_unique_blacklist_term(path, "python automation")

            self.assertFalse(added)
            self.assertEqual(
                path.read_text(encoding="utf-8"),
                "Python Automation\n",
            )

    def test_rejects_empty_selection(self) -> None:
        with TemporaryDirectory() as directory:
            path = Path(directory) / "blocked.txt"
            with self.assertRaises(ValueError):
                append_unique_blacklist_term(path, "   ")
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
