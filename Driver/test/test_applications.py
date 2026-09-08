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

from db.applications import create_for_source_urls
from db.applications import list_applications
from db.migrate import migrate_database
from db.readable_text import save_collected_job


class ApplicationsTest(unittest.TestCase):
    def test_uses_collector_owned_url_title_and_company(self) -> None:
        with TemporaryDirectory() as directory:
            db_path = Path(directory) / "jobs.sqlite"
            migrate_database(db_path)
            with closing(sqlite3.connect(db_path)) as connection:
                connection.row_factory = sqlite3.Row
                source_job_ref = save_collected_job(
                    connection,
                    "linkedin",
                    "123",
                    "Vacancy text",
                    source_url="https://www.linkedin.com/jobs/view/123",
                    title="Java Developer",
                    company="Example Company",
                )
                connection.execute(
                    "INSERT INTO jobs (source_job_ref) VALUES (?)",
                    (source_job_ref,),
                )

                created = create_for_source_urls(
                    connection,
                    ["https://www.linkedin.com/jobs/view/123"],
                    "2026-09-08",
                )
                rows = list_applications(connection)

            self.assertEqual(created, 1)
            self.assertEqual(len(rows), 1)
            self.assertEqual(rows[0]["source_url"], "https://www.linkedin.com/jobs/view/123")
            self.assertEqual(rows[0]["title"], "Java Developer")
            self.assertEqual(rows[0]["company"], "Example Company")


if __name__ == "__main__":
    unittest.main()
