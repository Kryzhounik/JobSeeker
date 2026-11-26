from __future__ import annotations

import copy
import sqlite3
import sys
import unittest
from pathlib import Path


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from contracts.validate_json import SchemaValidationError, validate_json
from db.job_mapper import apply_schema, save_job_json
from db.job_registry import insert_source_job
from db.readable_text import save_collected_job


DATABASE_SCHEMA = DRIVER_ROOT / "db" / "schema.sql"
ANALYSIS_SCHEMA = DRIVER_ROOT / "contracts" / "job_analysis.schema.json"


def complete_scored_record() -> dict:
    return {
        "title": "Backend Developer",
        "company": "Example Company",
        "location": "Chisinau",
        "remote_type": "remote",
        "remote_scope": "Moldova",
        "relocation": "NO",
        "seniority": "middle",
        "role": "backend",
        "salary": "",
        "summary": "Build backend services.",
        "notes": "",
        "job_interest": 3,
        "candidate_fit_percent": 75,
        "candidate_fit_reason_code": "ok",
        "candidate_fit_reason": "Relevant backend experience.",
        "languages": [],
        "technologies": [],
    }


def complete_fact_record() -> dict:
    return {
        "title": "Backend Developer",
        "company": "Example Company",
        "location": "Chisinau",
        "remote_type": "remote",
        "remote_scope": "Moldova",
        "relocation": "NO",
        "seniority": "middle",
        "role": "backend",
        "salary": "",
        "summary": "Build backend services.",
        "notes": "",
        "languages": [],
        "technologies": [],
    }


class AddedAtTest(unittest.TestCase):
    def setUp(self) -> None:
        self.connection = sqlite3.connect(":memory:")
        apply_schema(self.connection, DATABASE_SCHEMA)
        insert_source_job(self.connection, "linkedin", "123")
        save_collected_job(
            self.connection,
            "linkedin",
            "123",
            "Backend vacancy readable text",
            source_url="https://www.linkedin.com/jobs/view/123",
            title="Backend Developer",
            company="Example Company",
        )

    def tearDown(self) -> None:
        self.connection.close()

    def save(self, record: dict) -> int:
        return save_job_json(
            self.connection,
            record,
            source="linkedin",
            source_job_id="123",
        )

    def stored_date(self) -> str:
        return str(
            self.connection.execute(
                "SELECT added_at FROM jobs"
            ).fetchone()[0]
        )

    def test_assigns_local_date_when_added_at_is_absent(self) -> None:
        before = self.connection.execute(
            "SELECT date('now', 'localtime')"
        ).fetchone()[0]

        self.save(complete_scored_record())

        after = self.connection.execute(
            "SELECT date('now', 'localtime')"
        ).fetchone()[0]
        self.assertIn(self.stored_date(), {before, after})

    def test_ignores_legacy_payload_added_at_on_first_save(self) -> None:
        record = complete_scored_record()
        record["added_at"] = "1900-01-01"

        self.save(record)

        today = self.connection.execute(
            "SELECT date('now', 'localtime')"
        ).fetchone()[0]
        self.assertEqual(self.stored_date(), today)

    def test_upsert_preserves_existing_added_at_and_updates_other_fields(self) -> None:
        self.connection.execute(
            "INSERT INTO jobs (source_job_ref, added_at) "
            "SELECT id, ? FROM source_jobs WHERE source = ? AND source_job_id = ?",
            ("2001-02-03", "linkedin", "123"),
        )
        self.connection.commit()
        record = complete_scored_record()
        record["summary"] = "Updated summary."
        record["added_at"] = "1900-01-01"

        self.save(record)

        saved = self.connection.execute(
            "SELECT added_at, summary FROM jobs"
        ).fetchone()
        self.assertEqual(saved[0], "2001-02-03")
        self.assertEqual(saved[1], "Updated summary.")

    def test_analysis_schema_accepts_facts_without_added_at(self) -> None:
        facts = complete_fact_record()
        validate_json(facts, ANALYSIS_SCHEMA)

        facts_with_metadata = copy.deepcopy(facts)
        facts_with_metadata["added_at"] = "2026-11-26"
        with self.assertRaises(SchemaValidationError):
            validate_json(facts_with_metadata, ANALYSIS_SCHEMA)


if __name__ == "__main__":
    unittest.main()
