from __future__ import annotations

import sqlite3
import sys
from contextlib import closing
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from collector.filtering.linkedin_filter import DEFAULT_PREVIEW_CONFIG
from collector.filtering.linkedin_filter import VacancyFilter
from collector.filtering.linkedin_filter import apply_preview_decision
from db.config import COMPANY_FILTER
from db.config import LANGUAGE_FILTER
from db.config import TECHNOLOGY_FILTER
from db.config import TITLE_FILTER
from db.config import config_enabled
from db.config import load_filter_switches
from db.filter_rejections import save_content_filter_rejection
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

    def test_filter_loads_database_switches_once_per_operation(self) -> None:
        with patch(
            "collector.filtering.linkedin_filter.load_filter_switches",
            wraps=load_filter_switches,
        ) as loader:
            vacancy_filter = VacancyFilter(db_path=self.db_path)
            vacancy_filter.filter_title("Backend Engineer")
            vacancy_filter.filter_company("Ordinary Company")
            vacancy_filter.filter_text("Backend Engineer", "Java experience")

        self.assertEqual(loader.call_count, 1)

    def test_new_filter_instance_refreshes_database_snapshot(self) -> None:
        first_filter = VacancyFilter(db_path=self.db_path)
        self.disable(TITLE_FILTER)

        first_result = first_filter.filter_title("Senior Python Developer")
        refreshed_result = VacancyFilter(db_path=self.db_path).filter_title(
            "Senior Python Developer"
        )

        self.assertTrue(first_result.rejected)
        self.assertFalse(refreshed_result.rejected)

    def test_preview_batch_uses_one_database_snapshot(self) -> None:
        payload = [
            {"title": "Backend Engineer", "company": "First Company"},
            {"title": "Java Engineer", "company": "Second Company"},
        ]
        with (
            patch(
                "collector.filtering.linkedin_filter.load_filter_switches",
                wraps=load_filter_switches,
            ) as loader,
            patch(
                "collector.filtering.linkedin_filter.record_preview_filter",
                side_effect=lambda item, _db_path: item,
            ),
        ):
            apply_preview_decision(
                payload,
                DEFAULT_PREVIEW_CONFIG,
                self.db_path,
            )

        self.assertEqual(loader.call_count, 1)

    def test_preview_rejections_store_filter_rule(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "INSERT INTO companies (name, blacklisted) VALUES (?, 1)",
                ("Blocked Company",),
            )
            connection.commit()

        apply_preview_decision(
            [
                {
                    "title": "Senior Python Developer",
                    "company": "Ordinary Company",
                    "source_url": "https://example.test/title",
                },
                {
                    "title": "Backend Engineer",
                    "company": "Blocked Company",
                    "source_url": "https://example.test/company",
                },
            ],
            DEFAULT_PREVIEW_CONFIG,
            self.db_path,
        )

        with closing(self.connect()) as connection:
            rows = connection.execute(
                """
                SELECT title, blocked_term, rule
                FROM preview_filter_rejections
                ORDER BY rule
                """
            ).fetchall()

        self.assertEqual(
            rows,
            [
                (
                    "Backend Engineer",
                    "Blocked Company",
                    "company_blacklisted",
                ),
                (
                    "Senior Python Developer",
                    "Python",
                    "title_blocked",
                ),
            ],
        )

    def test_content_rejections_store_match_keyword_and_pattern(self) -> None:
        with closing(self.connect()) as connection:
            connection.executemany(
                """
                INSERT INTO source_jobs (
                    source,
                    source_job_id,
                    processing_status
                ) VALUES ('linkedin', ?, 'CLEANED')
                """,
                [("language-log",), ("technology-log",)],
            )
            connection.commit()

        vacancy_filter = VacancyFilter(db_path=self.db_path)
        cases = [
            ("language-log", "Fluent in English"),
            ("technology-log", "Proficiency in Python"),
        ]
        with closing(self.connect()) as connection:
            for source_job_id, text in cases:
                result = vacancy_filter.filter_text("Backend Engineer", text)
                self.assertTrue(result.rejected)
                save_content_filter_rejection(
                    connection,
                    "linkedin",
                    source_job_id,
                    rule=result.rule,
                    matched_text=result.match,
                    keyword_patterns=result.keyword_patterns,
                )
            connection.commit()
            rows = connection.execute(
                """
                SELECT rule, matched_text, matched_keyword, matched_pattern
                FROM content_filter_rejections
                ORDER BY rule
                """
            ).fetchall()

        self.assertEqual(rows[0][:3], (
            "hard_blocked_technology",
            "Proficiency in Python",
            "Python",
        ))
        self.assertIn("{technology}", rows[0][3])
        self.assertEqual(rows[1][:3], (
            "hard_language_requirement",
            "Fluent in English",
            "English C1",
        ))
        self.assertIn("{language}", rows[1][3])

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
