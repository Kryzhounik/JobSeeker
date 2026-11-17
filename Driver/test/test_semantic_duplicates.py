from contextlib import closing
from pathlib import Path
import sqlite3
import tempfile
import unittest
from Driver.db.migrate import migrate_database
from Driver.db.semantic_duplicates import load_batch, load_snapshot, save_decision, save_link


class SemanticDuplicatesTest(unittest.TestCase):
    def test_snapshots_links_and_run_boundaries(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'jobs.sqlite'
            migrate_database(path)
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                c.execute('PRAGMA foreign_keys=ON')
                for run in ('current', 'past'):
                    c.execute("INSERT INTO collection_runs(run_id,config_json) VALUES (?, '{}')", (run,))
                for ident, run, status in [(20,'current','CLEANED'),(3,'current','CLEANED'),(30,'past','CLEANED'),(40,'current','RAW'),(50,'current','CONTENT_REJECTED')]:
                    c.execute("INSERT INTO source_jobs(id,source,source_job_id,processing_status,collection_run_id) VALUES (?,'linkedin',?,?,?)", (ident,str(ident),status,run))
                    c.execute("INSERT INTO source_job_texts(source_job_ref,title,readable_text) VALUES (?,'Java Engineer',?)", (ident,'Saved text '+str(ident)))
                c.commit()
                c.execute("UPDATE collection_runs SET status='complete' WHERE run_id='current'")
                self.assertEqual([3, 20], [row['id'] for row in load_batch(c, 'current')])
                self.assertEqual([(False, None), (False, None)], [(row['checked'], row['duplicateOf']) for row in load_batch(c, 'current')])
                self.assertEqual('Saved text 3', load_batch(c, 'current')[0]['text'])
                with self.assertRaises(ValueError):
                    load_batch(c, 'missing')
                self.assertEqual('Saved text 20',load_snapshot(c,'current','linkedin','20')['text'])
                for ident in ('30','40','50'):
                    with self.assertRaises(ValueError): load_snapshot(c,'current','linkedin',ident)
                save_link(c,'current',3,20)
                self.assertEqual(20,c.execute('SELECT semantic_duplicate_of FROM source_jobs WHERE id=3').fetchone()[0])
                for pair in [(20,20),(20,30),(30,20)]:
                    with self.assertRaises(ValueError): save_link(c,'current',*pair)
                c.execute('DELETE FROM source_jobs WHERE id=20')
                c.commit()
                self.assertIsNone(c.execute('SELECT semantic_duplicate_of FROM source_jobs WHERE id=3').fetchone()[0])

    def test_decisions_mark_checked_atomically_and_validate_links(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'jobs.sqlite'
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                c.execute("INSERT INTO collection_runs(run_id,config_json) VALUES ('run','{}')")
                for ident in (1, 2):
                    c.execute("INSERT INTO source_jobs(id,source,source_job_id,processing_status,collection_run_id) VALUES (?,'linkedin',?,'CLEANED','run')", (ident, str(ident)))
                c.commit()
                save_decision(c, 'run', 1, None)
                save_decision(c, 'run', 2, 1)
                self.assertEqual([(1, 1, None), (2, 1, 1)], c.execute('SELECT id,dedup_checked,semantic_duplicate_of FROM source_jobs ORDER BY id').fetchall())
                for args in [(1, 1), (1, 999)]:
                    with self.assertRaises(ValueError): save_decision(c, 'run', *args)
                self.assertEqual((1, None), c.execute('SELECT dedup_checked,semantic_duplicate_of FROM source_jobs WHERE id=1').fetchone())

    def test_migration_adds_unchecked_column_to_existing_database(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'jobs.sqlite'
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                c.execute('ALTER TABLE source_jobs DROP COLUMN dedup_checked')
                c.execute("DELETE FROM schema_migrations WHERE version='050_dedup_checked'")
                c.commit()
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                column = next(row for row in c.execute('PRAGMA table_info(source_jobs)') if row[1] == 'dedup_checked')
                self.assertEqual('0', column[4])

    def test_upgrades_database_without_the_new_column(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'jobs.sqlite'
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                c.execute('ALTER TABLE source_jobs DROP COLUMN semantic_duplicate_of')
                c.execute("DELETE FROM schema_migrations WHERE version='046_semantic_duplicates'")
                c.commit()
            migrate_database(path)
            with closing(sqlite3.connect(path)) as c:
                self.assertIn('semantic_duplicate_of', [row[1] for row in c.execute('PRAGMA table_info(source_jobs)')])
