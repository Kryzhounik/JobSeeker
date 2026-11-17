"""Stateless JPy conversion adapter over the shared database layer."""
from contextlib import closing
import json
import sqlite3
from pathlib import Path
from db import semantic_duplicates
from db.migrate import migrate_database


def load_batch(database: str, batch_id: str) -> str:
    migrate_database(Path(database))
    with closing(sqlite3.connect(database)) as connection:
        return json.dumps(semantic_duplicates.load_batch(connection, batch_id), ensure_ascii=False)


def save_decision(database: str, batch_id: str, duplicate: int, original: int | None) -> None:
    with closing(sqlite3.connect(database)) as connection:
        connection.execute('PRAGMA foreign_keys = ON')
        semantic_duplicates.save_decision(connection, batch_id, duplicate, original)
