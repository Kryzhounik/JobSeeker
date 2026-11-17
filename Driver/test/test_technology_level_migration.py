from contextlib import closing
from pathlib import Path
import sqlite3
import sys
from tempfile import TemporaryDirectory
import unittest


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.migrate import MIGRATIONS_DIR, SCHEMA_PATH, migrate_database
from db.job_registry import insert_source_job


class TechnologyLevelMigrationTest(unittest.TestCase):
    def test_existing_rows_survive_and_familiar_can_be_saved(self):
        with TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            with closing(sqlite3.connect(database)) as connection:
                old_schema = SCHEMA_PATH.read_text(encoding="utf-8").replace(
                    "level IN ('familiar',", "level IN ('nice to have',",
                )
                connection.executescript(old_schema)
                connection.execute("CREATE TABLE schema_migrations (version TEXT PRIMARY KEY)")
                connection.executemany(
                    "INSERT INTO schema_migrations VALUES (?)",
                    [(p.stem,) for p in MIGRATIONS_DIR.glob("*.sql") if not p.name.startswith("049_")],
                )
                insert_source_job(connection, "linkedin", "1")
                connection.execute("INSERT INTO jobs (source_job_ref) SELECT id FROM source_jobs")
                connection.execute("INSERT INTO technologies (name) VALUES ('Java')")
                connection.execute(
                    "INSERT INTO job_technologies VALUES (7, 1, 1, 'required', 'nice to have', 1, 'original')"
                )
                connection.commit()
            migrate_database(database)
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                self.assertEqual(connection.execute("SELECT * FROM job_technologies").fetchall(),
                                 [(7, 1, 1, "required", "familiar", 1, "original")])
                connection.execute(
                    "INSERT INTO job_technologies (job_id, technology_id, requirement_type, level, level_rank) "
                    "VALUES (1, 1, 'desired', 'familiar', 1)"
                )
                self.assertEqual(connection.execute("PRAGMA foreign_key_check").fetchall(), [])
                connection.execute("SELECT * FROM job_list").fetchall()
                connection.execute("SELECT * FROM job_view").fetchall()

    def test_fresh_database_accepts_familiar(self):
        with TemporaryDirectory() as directory:
            database = Path(directory) / "jobs.sqlite"
            migrate_database(database)
            with closing(sqlite3.connect(database)) as connection:
                ddl = connection.execute(
                    "SELECT sql FROM sqlite_master WHERE name='job_technologies'"
                ).fetchone()[0]
                self.assertIn("'familiar'", ddl)
                self.assertNotIn("'nice to have'", ddl)
