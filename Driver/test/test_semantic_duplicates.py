from contextlib import closing
from pathlib import Path
import sqlite3
import tempfile
import unittest
from Driver.db.migrate import migrate_database
from Driver.db.semantic_duplicates import load_batch, load_snapshot, save_link


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
