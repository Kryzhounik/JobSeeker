from __future__ import annotations

import sqlite3


def ensure_jobs_table(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_url TEXT NOT NULL UNIQUE,
            title TEXT NOT NULL
        )
        """
    )


def analyzed_urls(connection: sqlite3.Connection) -> set[str]:
    ensure_jobs_table(connection)
    return {
        row[0]
        for row in connection.execute("SELECT source_url FROM jobs").fetchall()
    }
