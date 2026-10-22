"""GPT evaluation storage checks against a new disposable database only."""

import json
import sqlite3
import tempfile
import unittest
import subprocess
import sys
from contextlib import closing
from pathlib import Path

from GUI import client_data
from db.migrate import MIGRATIONS_DIR, SCHEMA_PATH, migrate_database
from db.gpt_evaluations import find_job_id


class GptEvaluationsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.database = Path(self.directory.name) / "jobs.sqlite"
        with closing(sqlite3.connect(self.database)) as connection:
            connection.executescript(SCHEMA_PATH.read_text(encoding="utf-8"))
            connection.executemany(
                "INSERT INTO source_jobs (id, source, source_job_id, processing_status) "
                "VALUES (?, 'linkedin', ?, 'SAVED')", [(1, 'first'), (2, 'second')],
            )
            connection.executemany(
                "INSERT INTO jobs (id, source_job_ref) VALUES (?, ?)", [(1, 1), (2, 2)],
            )
            connection.executemany(
                "INSERT INTO source_job_texts (source_job_ref, readable_text, source_url) VALUES (?, 'Vacancy', ?)",
                [(1, 'https://example.com/1'), (2, 'https://example.com/2')],
            )
            connection.execute("CREATE TABLE schema_migrations (version TEXT PRIMARY KEY)")
            connection.executemany(
                "INSERT INTO schema_migrations (version) VALUES (?)",
                [(p.stem,) for p in MIGRATIONS_DIR.glob('*.sql')
                 if p.stem != '041_job_gpt_evaluations'],
            )
            connection.commit()

    def load(self, job_id):
        return json.loads(client_data.load_gpt_evaluation(self.database, job_id))

    def test_lookup_finds_only_a_saved_job_and_unknown_url_is_normal(self):
        with closing(sqlite3.connect(self.database)) as connection:
            self.assertEqual(find_job_id(connection, 'https://example.com/1'), 1)
            self.assertIsNone(find_job_id(connection, 'https://example.com/missing'))

    def test_process_bridge_reads_and_saves_without_gui_or_real_session(self):
        script = SCHEMA_PATH.parent / 'gpt_evaluations.py'

        def request(payload):
            process = subprocess.run(
                [sys.executable, str(script), str(self.database)],
                input=json.dumps(payload, ensure_ascii=False).encode('utf-8'),
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
            )
            response = json.loads(process.stdout.decode('utf-8'))
            return process.returncode, response

        self.assertEqual(request(dict(operation='find_job_id', url='https://example.com/1')),
                         (0, dict(result=1)))
        self.assertEqual(request(dict(operation='find_job_id', url='https://example.com/absent')),
                         (0, dict(result=None)))
        answer = 'Ответ GPT\n\n' + 'Полный текст. ' * 1000
        self.assertEqual(request(dict(operation='save', job_id=1, answer=answer, evaluated_at='now')),
                         (0, dict(result=None)))
        self.assertEqual(self.load(1)['answer'], answer)
        code, response = request(dict(operation='save', job_id=1, answer='', evaluated_at='after'))
        self.assertEqual(code, 1)
        self.assertIn('error', response)
        self.assertEqual(self.load(1)['answer'], answer)

    def test_save_full_answer_upsert_and_read_by_original_id(self):
        self.assertIsNone(self.load(1))
        answer = "Полный ответ\n\nJava, Spring — подходит.\n" + "длинный текст " * 1000
        client_data.save_gpt_evaluation(self.database, 1, answer, "2026-10-22T14:00:00Z")
        client_data.save_gpt_evaluation(self.database, 2, "Other job", "2026-10-22T14:01:00Z")
        self.assertEqual(self.load(1), dict(job_id=1, answer=answer, evaluated_at="2026-10-22T14:00:00Z"))
        client_data.save_gpt_evaluation(self.database, 1, "New answer", "2026-10-22T15:00:00Z")
        self.assertEqual(self.load(1)['answer'], "New answer")
        self.assertEqual(self.load(1)['evaluated_at'], "2026-10-22T15:00:00Z")
        self.assertEqual(self.load(2)['answer'], "Other job")
        with closing(sqlite3.connect(self.database)) as connection:
            self.assertEqual(connection.execute(
                "SELECT status, candidate_fit_percent, job_interest FROM jobs WHERE id = 1",
            ).fetchone(), ('New', 100, 0))

    def test_failed_write_preserves_previous_and_foreign_key_is_enforced(self):
        client_data.save_gpt_evaluation(self.database, 1, "Previous", "before")
        with self.assertRaises(sqlite3.IntegrityError):
            client_data.save_gpt_evaluation(self.database, 1, "", "after")
        self.assertEqual(self.load(1)['answer'], "Previous")
        self.assertEqual(self.load(1)['evaluated_at'], "before")
        with self.assertRaises(sqlite3.IntegrityError):
            client_data.save_gpt_evaluation(self.database, 999, "Missing job", "now")

    def test_schema_cascades_when_job_deleted(self):
        client_data.save_gpt_evaluation(self.database, 1, "Answer", "now")
        with closing(sqlite3.connect(self.database)) as connection:
            connection.execute("PRAGMA foreign_keys = ON")
            connection.execute("DELETE FROM jobs WHERE id = 1")
            connection.commit()
        self.assertIsNone(self.load(1))

    def test_migration_adds_table_to_existing_schema_and_is_repeatable(self):
        with closing(sqlite3.connect(self.database)) as connection:
            connection.execute("DROP TABLE job_gpt_evaluations")
            connection.commit()
        migrate_database(self.database)
        client_data.save_gpt_evaluation(self.database, 1, "Migrated", "now")
        migrate_database(self.database)
        self.assertEqual(self.load(1)['answer'], "Migrated")
        with closing(sqlite3.connect(self.database)) as connection:
            self.assertEqual(connection.execute(
                "SELECT count(*) FROM schema_migrations WHERE version = '041_job_gpt_evaluations'",
            ).fetchone()[0], 1)
            foreign_key = connection.execute("PRAGMA foreign_key_list(job_gpt_evaluations)").fetchone()
            self.assertEqual((foreign_key[2], foreign_key[3], foreign_key[6]), ('jobs', 'job_id', 'CASCADE'))


if __name__ == '__main__':
    unittest.main()
