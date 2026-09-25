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

from db.migrate import migrate_database


class CandidateFitReasonCodesTest(unittest.TestCase):
    def test_database_owns_candidate_fit_reason_codes(self) -> None:
        with TemporaryDirectory() as directory:
            db_path = Path(directory) / "jobs.sqlite"
            migrate_database(db_path)

            with closing(sqlite3.connect(db_path)) as connection:
                connection.execute("PRAGMA foreign_keys = ON")
                reason_count = connection.execute(
                    "SELECT count(*) FROM candidate_fit_reason_codes"
                ).fetchone()[0]
                missing_description_count = connection.execute(
                    """
                    SELECT count(*)
                    FROM candidate_fit_reason_codes
                    WHERE length(trim(description)) = 0
                    """
                ).fetchone()[0]
                reason_foreign_key = any(
                    row[2] == "candidate_fit_reason_codes"
                    and row[3] == "candidate_fit_reason_code"
                    and row[4] == "code"
                    for row in connection.execute(
                        "PRAGMA foreign_key_list(jobs)"
                    ).fetchall()
                )
                source_job_ref = connection.execute(
                    """
                    INSERT INTO source_jobs (
                        source,
                        source_job_id,
                        processing_status
                    )
                    VALUES ('linkedin', 'invalid-reason-test', 'ANALYZED')
                    RETURNING id
                    """
                ).fetchone()[0]

                with self.assertRaises(sqlite3.IntegrityError):
                    connection.execute(
                        """
                        INSERT INTO jobs (
                            source_job_ref,
                            candidate_fit_reason_code
                        )
                        VALUES (?, 'not-a-real-reason')
                        """,
                        (source_job_ref,),
                    )

            self.assertGreater(reason_count, 0)
            self.assertEqual(missing_description_count, 0)
            self.assertTrue(reason_foreign_key)


if __name__ == "__main__":
    unittest.main()
