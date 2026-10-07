"""Exercise ID-based GUI writes against a disposable database snapshot."""

import json
import sqlite3
import tempfile
import unittest
from contextlib import closing
from pathlib import Path

from GUI import client_data


class ClientDataSmoke(unittest.TestCase):
    def test_job_without_url_can_be_updated(self):
        source = Path(__file__).resolve().parents[1] / "Data" / "jobs.sqlite"
        if not source.is_file():
            self.skipTest("Local jobs database is unavailable")
        with tempfile.TemporaryDirectory() as directory:
            snapshot = Path(directory) / "jobs.sqlite"
            with closing(sqlite3.connect(source)) as original:
                with closing(sqlite3.connect(snapshot)) as copy:
                    original.backup(copy)

            statuses = json.loads(client_data.load_status_values(snapshot))
            jobs = json.loads(client_data.load_jobs(snapshot, json.dumps({
                "statuses": statuses, "show_zero": True,
            })))
            job = next(row for row in jobs if not row["source_url"])
            job_id = job["job_id"]
            detail = json.loads(client_data.load_job_detail(snapshot, job_id))["detail"]
            self.assertEqual(job_id, detail["id"])

            fit, interest = 37, 123
            score = client_data.save_scores(snapshot, job_id, fit, interest)
            detail = json.loads(client_data.load_job_detail(snapshot, job_id))["detail"]
            self.assertEqual(fit, detail["fit"])
            self.assertEqual(interest, detail["interest"])
            self.assertEqual(score, detail["score"])

            result = json.loads(client_data.set_job_status_by_ids(
                snapshot, json.dumps([job_id]), "Applied", "2026-10-07"
            ))
            self.assertEqual(1, result["updated_count"])
            with closing(sqlite3.connect(snapshot)) as connection:
                status = connection.execute(
                    "SELECT status FROM jobs WHERE id = ?", (job_id,)
                ).fetchone()[0]
                applications = connection.execute(
                    "SELECT count(*) FROM applications WHERE job_id = ?", (job_id,)
                ).fetchone()[0]
            self.assertEqual("Applied", status)
            self.assertEqual(1, applications)

            repeated = json.loads(client_data.set_job_status_by_ids(
                snapshot, json.dumps([job_id]), "Applied", "2026-10-07"
            ))
            self.assertEqual(0, repeated["created_applications"])


if __name__ == "__main__":
    unittest.main()
