"""Stateless JPy conversion adapter over the shared database layer."""
from contextlib import closing
import json
import sqlite3
from db import semantic_duplicates


def load_batch(database: str, batch_id: str) -> str:
    with closing(sqlite3.connect(database)) as connection:
        return json.dumps(semantic_duplicates.load_batch(connection, batch_id), ensure_ascii=False)


def save_link(database: str, batch_id: str, duplicate: int, original: int) -> None:
    with closing(sqlite3.connect(database)) as connection:
        connection.execute('PRAGMA foreign_keys = ON')
        semantic_duplicates.save_link(connection, batch_id, duplicate, original)
