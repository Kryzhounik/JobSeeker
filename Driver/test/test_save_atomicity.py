from __future__ import annotations

from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
from tempfile import TemporaryDirectory
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.migrate import migrate_database
from db.readable_text import save_collected_job
from db.save import save_records


def record(job_id: str) -> dict[str, object]:
    return {
        "source": "linkedin",
        "source_url": f"https://www.linkedin.com/jobs/view/{job_id}",
        "status": "New",
        "title": f"Java Developer {job_id}",
        "company": "Example",
        "location": "Remote",
        "remote_type": "remote",
        "remote_scope": "Worldwide",
        "relocation": "NO",
        "job_interest": 70,
        "candidate_fit_percent": 80,
        "candidate_fit_reason_code": "ok",
        "candidate_fit_reason": "Good fit",
        "seniority": "senior",
        "role": "backend",
        "salary": "",
        "summary": "Original summary",
        "notes": "",
        "added_at": "2026-09-26",
        "languages": [
            {"name": "English", "level": "B2", "level_rank": 4, "raw_value": "B2"}
        ],
        "technologies": [
            {
                "name": "Java",
                "requirement": "core",
                "level": "advanced",
                "level_rank": 4,
                "raw_value": "Java",
            }
        ],
    }


class SaveAtomicityTest(unittest.TestCase):
    def test_failed_records_roll_back_while_valid_neighbor_is_saved(self) -> None:
        with TemporaryDirectory() as directory:
            root = Path(directory)
            database = root / "jobs.sqlite"
            scored = root / "scored"
            scored.mkdir()
            migrate_database(database)

            with closing(sqlite3.connect(database)) as connection:
                connection.execute("PRAGMA foreign_keys = ON")
                for job_id in ("1", "2", "3"):
                    value = record(job_id)
                    save_collected_job(
                        connection,
                        "linkedin",
                        job_id,
                        "Collected vacancy text",
                        source_url=value["source_url"],
                        title=value["title"],
                        company=value["company"],
                        location=value["location"],
                        workplace="remote",
                        salary="",
                    )
                connection.commit()

            initial = record("3")
            (scored / "3.json").write_text(json.dumps(initial), encoding="utf-8")
            self.assertEqual(
                save_records(
                    str(scored), database, DRIVER_ROOT / "db/schema.sql",
                    "linkedin", False, ["3"],
                )[0][0],
                "save",
            )

            invalid_salary = record("1")
            invalid_salary["salary"] = "unknown"
            valid = record("2")
            invalid_update = record("3")
            invalid_update["summary"] = "Must be rolled back"
            invalid_update["languages"] = [
                {"name": "German", "level": "C1", "level_rank": 5, "raw_value": "C1"}
            ]
            invalid_update["technologies"] = [
                {
                    "name": "Kotlin",
                    "requirement": "core",
                    "level": "advanced",
                    "level_rank": "invalid",
                    "raw_value": "Kotlin",
                }
            ]
            for job_id, value in (
                ("1", invalid_salary),
                ("2", valid),
                ("3", invalid_update),
            ):
                (scored / f"{job_id}.json").write_text(
                    json.dumps(value), encoding="utf-8"
                )

            results = save_records(
                str(scored), database, DRIVER_ROOT / "db/schema.sql",
                "linkedin", True, ["1", "2", "3"],
            )
            self.assertEqual([status for status, _ in results], ["error", "save", "error"])

            with closing(sqlite3.connect(database)) as connection:
                statuses = dict(connection.execute(
                    "SELECT source_job_id, processing_status FROM source_jobs"
                ))
                saved_ids = {
                    row[0]
                    for row in connection.execute(
                        """
                        SELECT source_job.source_job_id
                        FROM jobs job
                        JOIN source_jobs source_job ON source_job.id = job.source_job_ref
                        """
                    )
                }
                preserved = connection.execute(
                    """
                    SELECT job.summary, language.name, technology.name
                    FROM jobs job
                    JOIN source_jobs source_job ON source_job.id = job.source_job_ref
                    JOIN job_languages job_language ON job_language.job_id = job.id
                    JOIN languages language ON language.id = job_language.language_id
                    JOIN job_technologies job_technology ON job_technology.job_id = job.id
                    JOIN technologies technology ON technology.id = job_technology.technology_id
                    WHERE source_job.source_job_id = '3'
                    """
                ).fetchone()

            self.assertEqual(statuses["1"], "CLEANED")
            self.assertEqual(statuses["2"], "SAVED")
            self.assertEqual(saved_ids, {"2", "3"})
            self.assertEqual(preserved, ("Original summary", "English", "Java"))


if __name__ == "__main__":
    unittest.main()
