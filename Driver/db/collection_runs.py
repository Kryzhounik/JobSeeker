"""Queries for the shared collection/workflow batch registry."""
import sqlite3


def list_runs(connection: sqlite3.Connection) -> list[dict]:
    cursor = connection.execute("""
        SELECT run_id, started_at, finished_at, status, stage,
               collection_status, collection_finished_at, accepted_count,
               stop_reason, message
        FROM collection_runs
        ORDER BY started_at DESC, run_id DESC
    """)
    names = [column[0] for column in cursor.description]
    return [dict(zip(names, row)) for row in cursor.fetchall()]
