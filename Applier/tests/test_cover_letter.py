"""Checks of the Python SQLite helper and migrations; no Java or model calls."""
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'Driver'))
from db.migrate import migrate_database


class CoverLetterStoreTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.database = Path(self.temp.name) / 'jobs.sqlite'
        migrate_database(self.database)
        with closing(sqlite3.connect(self.database)) as db:
            db.execute("INSERT INTO companies(id,name) VALUES (5,'Company')")
            db.execute("INSERT INTO source_jobs(id,source,source_job_id,processing_status) VALUES (17,'linkedin','999','RAW')")
            db.execute("INSERT INTO source_job_texts(source_job_ref,title,company_id,readable_text) VALUES (17,'Engineer',5,'Full description')")
            db.execute('INSERT INTO jobs(id,source_job_ref) VALUES (42,17)')
            db.commit()

    def helper(self, job_id=42, letter=None):
        args = [sys.executable, str(ROOT/'Applier/cover_letter_job.py'), str(self.database), str(job_id)]
        if letter is not None:
            args.append('--save')
        return subprocess.run(args, input=letter, capture_output=True, encoding='utf-8')

    def test_reads_complete_vacancy_and_reports_missing_job(self):
        result = self.helper()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {
            'job_title': 'Engineer', 'company': 'Company', 'job_description': 'Full description'})
        self.assertNotEqual(self.helper(999).returncode, 0)

    def test_stores_one_letter_and_deletes_it_with_job(self):
        letter = 'Letter — спасибо\n'
        self.assertEqual(self.helper(letter=letter).returncode, 0)
        self.assertEqual(json.loads(self.helper().stdout), {'letter': letter})
        self.assertNotEqual(self.helper(letter='Another letter').returncode, 0)
        with closing(sqlite3.connect(self.database)) as db:
            db.execute('PRAGMA foreign_keys = ON')
            db.execute('DELETE FROM jobs WHERE id = 42')
            db.commit()
            self.assertEqual(db.execute('SELECT count(*) FROM cover_letters').fetchone()[0], 0)

    def test_migration_preserves_only_last_old_letter(self):
        with closing(sqlite3.connect(self.database)) as db:
            db.executescript('''DROP TABLE cover_letters;
                CREATE TABLE cover_letters (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id INTEGER NOT NULL REFERENCES jobs(id),
                    letter_text TEXT NOT NULL,
                    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
                DELETE FROM schema_migrations WHERE version = '045_one_cover_letter_per_job';
                INSERT INTO cover_letters(job_id,letter_text) VALUES (42,'Earlier'),(42,'Last');''')
            db.commit()
        result = self.helper()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertEqual(json.loads(result.stdout), {'letter': 'Last'})
        self.assertNotEqual(self.helper(letter='Duplicate').returncode, 0)
        with closing(sqlite3.connect(self.database)) as db:
            self.assertEqual(db.execute('SELECT count(*) FROM cover_letters').fetchone()[0], 1)


if __name__ == '__main__':
    unittest.main()
