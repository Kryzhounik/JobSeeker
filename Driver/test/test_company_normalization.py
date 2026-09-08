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

from collector.filtering.linkedin_filter import Vacancy
from collector.filtering.linkedin_filter import VacancyFilter
from collector.filtering.linkedin_filter import decide_preview
from db.job_mapper import load_job_json
from db.job_mapper import save_job_json
from db.migrate import migrate_database
from db.readable_text import save_collected_job


class CompanyNormalizationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_directory = TemporaryDirectory()
        self.addCleanup(self.temp_directory.cleanup)
        self.db_path = Path(self.temp_directory.name) / "jobs.sqlite"
        migrate_database(self.db_path)

    def connect(self) -> sqlite3.Connection:
        connection = sqlite3.connect(self.db_path)
        connection.execute("PRAGMA foreign_keys = ON")
        return connection

    def test_mapper_normalizes_company_and_preserves_json_contract(self) -> None:
        record = {
            "source": "linkedin",
            "source_url": "https://www.linkedin.com/jobs/view/123",
            "status": "New",
            "title": "Senior Java Backend Developer",
            "company": "  Example   Company  ",
            "location": "Remote",
            "remote_type": "remote",
            "remote_scope": "EU",
            "relocation": "NO",
            "job_interest": 70,
            "candidate_fit_percent": 80,
            "candidate_fit_reason_code": "ok",
            "candidate_fit_reason": "ok",
            "seniority": "senior",
            "role": "backend",
            "salary": "",
            "summary": "Summary",
            "notes": "",
            "added_at": "2026-08-17",
            "languages": [],
            "technologies": [],
        }

        with closing(self.connect()) as connection:
            save_collected_job(
                connection,
                "linkedin",
                "123",
                "Collected vacancy text",
                source_url=record["source_url"],
                title=record["title"],
                company=record["company"],
                location="Remote",
                workplace="remote",
                salary="",
            )
            job_id = save_job_json(connection, record)
            connection.commit()
            company = connection.execute(
                "SELECT name, blacklisted FROM companies"
            ).fetchone()
            job_company_id = connection.execute(
                """
                SELECT text.company_id
                FROM jobs job
                JOIN source_job_texts text
                    ON text.source_job_ref = job.source_job_ref
                WHERE job.id = ?
                """,
                (job_id,),
            ).fetchone()[0]
            job_columns = {
                row[1]
                for row in connection.execute("PRAGMA table_info(jobs)")
            }
            loaded = load_job_json(connection, job_id=job_id)
            loaded_by_url = load_job_json(
                connection,
                source_url=record["source_url"],
            )

        self.assertEqual(company, ("Example Company", 0))
        self.assertIsNotNone(job_company_id)
        self.assertTrue(
            {"source_url", "title", "company_id"}.isdisjoint(job_columns)
        )
        self.assertEqual(loaded["company"], "Example Company")
        self.assertEqual(loaded_by_url, loaded)

    def test_preview_rejects_blacklisted_company_case_insensitively(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "INSERT INTO companies (name, blacklisted) VALUES (?, 1)",
                ("Blocked Company",),
            )
            connection.commit()

        result = decide_preview(
            {
                "title": "Senior Java Backend Developer",
                "company": "blocked company",
            },
            db_path=self.db_path,
        )

        self.assertEqual(result["preview_decision"], "skip")
        self.assertEqual(
            result["preview_reason"],
            "company blacklisted: blocked company",
        )
        self.assertEqual(result["preview_blocked_terms"], ["blocked company"])

    def test_full_filter_uses_the_same_company_blacklist(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "INSERT INTO companies (name, blacklisted) VALUES (?, 1)",
                ("Blocked Company",),
            )
            connection.commit()

        result = VacancyFilter(db_path=self.db_path).filter(
            Vacancy(
                title="Senior Java Backend Developer",
                text="Java experience",
                company="Blocked Company",
            )
        )

        self.assertTrue(result.rejected)
        self.assertEqual(result.rule, "company_blacklisted")

    def test_unlisted_company_does_not_block_preview(self) -> None:
        with closing(self.connect()) as connection:
            connection.execute(
                "INSERT INTO companies (name) VALUES (?)",
                ("Ordinary Company",),
            )
            connection.commit()

        result = decide_preview(
            {
                "title": "Senior Java Backend Developer",
                "company": "Ordinary Company",
            },
            db_path=self.db_path,
        )

        self.assertEqual(result["preview_decision"], "open")


if __name__ == "__main__":
    unittest.main()
