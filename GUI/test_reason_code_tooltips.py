from __future__ import annotations

import sqlite3
import unittest

from GUI.jobs_viewer import format_reason_code_reference
from GUI.jobs_viewer import load_reason_code_descriptions


class ReasonCodeTooltipTest(unittest.TestCase):
    def test_loads_descriptions_in_code_order(self) -> None:
        connection = sqlite3.connect(":memory:")
        self.addCleanup(connection.close)
        connection.executescript(
            """
            CREATE TABLE candidate_fit_reason_codes (
                code TEXT PRIMARY KEY,
                description TEXT NOT NULL
            );
            INSERT INTO candidate_fit_reason_codes VALUES
                ('tech', 'Technology mismatch.'),
                ('lang', 'Language mismatch.');
            """
        )

        descriptions = load_reason_code_descriptions(connection)

        self.assertEqual(
            descriptions,
            {
                "lang": "Language mismatch.",
                "tech": "Technology mismatch.",
            },
        )

    def test_formats_complete_header_reference(self) -> None:
        text = format_reason_code_reference(
            {
                "lang": "Language mismatch.",
                "tech": "Technology mismatch.",
            }
        )

        self.assertEqual(
            text,
            "lang: Language mismatch.\ntech: Technology mismatch.",
        )


if __name__ == "__main__":
    unittest.main()
