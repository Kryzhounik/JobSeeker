"""Apply SQLite schema migrations for JobSeeker."""

from __future__ import annotations

import argparse
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.job_mapper import apply_schema
from db.job_registry import SOURCE_CODES
from db.job_registry import mark_status


MIGRATIONS_DIR = ROOT / "db" / "migrations"
SCHEMA_PATH = ROOT / "db" / "schema.sql"


def ensure_migration_table(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version TEXT PRIMARY KEY,
            applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
        """
    )


def applied_versions(connection: sqlite3.Connection) -> set[str]:
    ensure_migration_table(connection)
    return {
        row[0]
        for row in connection.execute(
            "SELECT version FROM schema_migrations"
        ).fetchall()
    }


def migration_files(migrations_dir: Path) -> list[Path]:
    return sorted(migrations_dir.glob("*.sql"))


def table_exists(connection: sqlite3.Connection, table: str) -> bool:
    return (
        connection.execute(
            """
            SELECT 1
            FROM sqlite_master
            WHERE type = 'table' AND name = ?
            """,
            (table,),
        ).fetchone()
        is not None
    )


def migration_already_effective(
    connection: sqlite3.Connection,
    version: str,
) -> bool:
    row = connection.execute(
        """
        SELECT sql
        FROM sqlite_master
        WHERE type = 'table' AND name = 'jobs'
        """
    ).fetchone()
    ddl = row[0] if row else ""
    if version == "001_job_statuses_table":
        return "REFERENCES job_statuses(code)" in ddl
    if version == "003_source_job_registry":
        return (
            "source_job_ref" in ddl
            and table_exists(connection, "source_jobs")
        )
    return False


def backfill_processing_registry(
    connection: sqlite3.Connection,
    data_root: Path,
) -> int:
    stage_dirs = (
        ("RAW", data_root / "raw", "pages", "*.html"),
        ("CLEANED", data_root / "readable_v2", "pages", "*.txt"),
        ("ANALYZED", data_root / "analyzed", "", "*.json"),
        ("SCORED", data_root / "scored", "", "*.json"),
    )
    before = int(connection.execute("SELECT count(*) FROM source_jobs").fetchone()[0])
    for status, root, suffix, pattern in stage_dirs:
        for source in SOURCE_CODES:
            directory = root / source
            if suffix:
                directory /= suffix
            if not directory.exists():
                continue
            for path in directory.glob(pattern):
                mark_status(connection, source, path.stem, status)
    after = int(connection.execute("SELECT count(*) FROM source_jobs").fetchone()[0])
    return after - before


def apply_migration(connection: sqlite3.Connection, path: Path) -> str:
    version = path.stem
    if migration_already_effective(connection, version):
        connection.execute(
            "INSERT OR IGNORE INTO schema_migrations (version) VALUES (?)",
            (version,),
        )
        return f"already-effective {version}"

    sql = path.read_text(encoding="utf-8")
    connection.executescript(sql)
    connection.execute(
        "INSERT INTO schema_migrations (version) VALUES (?)",
        (version,),
    )
    return f"applied {version}"


def migrate_database(
    db_path: Path,
    migrations_dir: Path = MIGRATIONS_DIR,
    schema_path: Path = SCHEMA_PATH,
) -> list[str]:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    messages: list[str] = []

    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        schema_changed = False
        registry_migrated = False
        if not table_exists(connection, "jobs"):
            apply_schema(connection, schema_path)
            connection.commit()
            schema_changed = True

        ensure_migration_table(connection)
        done = applied_versions(connection)

        for path in migration_files(migrations_dir):
            version = path.stem
            if version in done:
                messages.append(f"skip {version}")
                continue
            message = apply_migration(connection, path)
            messages.append(message)
            schema_changed = True
            if version == "003_source_job_registry":
                registry_migrated = True
            connection.commit()

        if schema_changed:
            apply_schema(connection, schema_path)
        if registry_migrated:
            count = backfill_processing_registry(connection, db_path.parent)
            messages.append(f"backfilled source_jobs +{count}")
        connection.commit()

    return messages


def main() -> None:
    parser = argparse.ArgumentParser(description="Apply JobSeeker DB migrations.")
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("--migrations-dir", default=str(MIGRATIONS_DIR))
    parser.add_argument("--schema", default=str(SCHEMA_PATH))
    args = parser.parse_args()

    messages = migrate_database(
        db_path=Path(args.db),
        migrations_dir=Path(args.migrations_dir),
        schema_path=Path(args.schema),
    )
    for message in messages:
        print(message)
    print("database schema is up to date")


if __name__ == "__main__":
    main()
