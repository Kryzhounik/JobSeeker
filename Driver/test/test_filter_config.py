from __future__ import annotations

import sqlite3
import sys
from contextlib import closing
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.linkedin_filter import VacancyFilter
from db.config import COMPANY_FILTER
from db.config import LANGUAGE_FILTER
from db.config import TECHNOLOGY_FILTER
from db.config import TITLE_FILTER
from db.config import config_enabled
from db.migrate import migrate_database


class FilterConfigTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.db_path = Path(self.temp_directory.name) / "jobs.sqlite"
        migrate_database(self.db_path)

    def connect(self) -> sqlite3.Connection:
        return sqlite3.connect(self.db_path)

    def disable(self, config_name: str) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "UPDATE config SET value = '0' WHERE config_name = ?",
                (config_name,),
            )
            connection.commit()

    def test_migration_creates_enabled_filter_defaults(self) -> None:
        with closing(self.connect()) as connection:
            columns = [
                row[1]
                for row in connection.execute("PRAGMA table_info(config)").fetchall()
            ]
            values = dict(
                connection.execute(
                    "SELECT config_name, value FROM config ORDER BY config_name"
                ).fetchall()
            )

        self.assertEqual(columns, ["key", "config_name", "value"])
        self.assertEqual(
            values,
            {
                COMPANY_FILTER: "1",
                LANGUAGE_FILTER: "1",
                TECHNOLOGY_FILTER: "1",
                TITLE_FILTER: "1",
            },
        )

    def test_config_enabled_reads_current_database_value(self) -> None:
        with closing(self.connect()) as connection:
            self.assertTrue(config_enabled(connection, TITLE_FILTER))
            connection.execute(
                "UPDATE config SET value = '0' WHERE config_name = ?",
                (TITLE_FILTER,),
            )
            self.assertFalse(config_enabled(connection, TITLE_FILTER))

    def test_disabled_title_filter_does_not_block_title(self) -> None:
        self.disable(TITLE_FILTER)

        result = VacancyFilter(db_path=self.db_path).filter_title(
            "Senior Python Developer"
        )

        self.assertFalse(result.rejected)
        self.assertEqual(result.reason, "title filter disabled")

    def test_disabled_company_filter_does_not_block_company(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "INSERT INTO companies (name, blacklisted) VALUES (?, 1)",
                ("Blocked Company",),
            )
            connection.execute(
                "UPDATE config SET value = '0' WHERE config_name = ?",
                (COMPANY_FILTER,),
            )
            connection.commit()

        result = VacancyFilter(db_path=self.db_path).filter_company(
            "Blocked Company"
        )

        self.assertFalse(result.rejected)
        self.assertEqual(result.reason, "company filter disabled")

    def test_disabled_technology_filter_does_not_block_requirement(self) -> None:
        self.disable(TECHNOLOGY_FILTER)

        result = VacancyFilter(db_path=self.db_path).filter_text(
            "Backend Engineer",
            "Proficiency in Python",
        )

        self.assertFalse(result.rejected)
        self.assertEqual(result.reason, "technology content filter disabled")

    def test_disabled_language_filter_does_not_block_requirement(self) -> None:
        self.disable(LANGUAGE_FILTER)

        result = VacancyFilter(db_path=self.db_path).filter_text(
            "Backend Engineer",
            "Fluent in English",
        )

        self.assertFalse(result.rejected)
        self.assertEqual(result.reason, "no content skip signals")


if __name__ == "__main__":
    unittest.main()
