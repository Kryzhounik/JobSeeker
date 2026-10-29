"""Last successful ChatGPT answer associated with a persisted vacancy."""

from __future__ import annotations

import sqlite3
from contextlib import closing
import json
from pathlib import Path
import sys


def find_job_id(connection: sqlite3.Connection, url: str) -> int | None:
    row = connection.execute(
        """
        SELECT job.id FROM jobs job
        JOIN source_job_texts text ON text.source_job_ref = job.source_job_ref
        WHERE text.source_url = ?
        """,
        (url,),
    ).fetchone()
    return None if row is None else int(row[0])


def save_evaluation(
    connection: sqlite3.Connection, job_id: int, answer: str, evaluated_at: str,
) -> None:
    connection.execute(
        """
        INSERT INTO job_gpt_evaluations (job_id, answer, evaluated_at)
        VALUES (?, ?, ?)
        ON CONFLICT(job_id) DO UPDATE SET
            answer = excluded.answer,
            evaluated_at = excluded.evaluated_at
        """,
        (job_id, answer, evaluated_at),
    )


def load_evaluation(connection: sqlite3.Connection, job_id: int) -> dict | None:
    row = connection.execute(
        "SELECT job_id, answer, evaluated_at FROM job_gpt_evaluations WHERE job_id = ?",
        (job_id,),
    ).fetchone()
    if row is None:
        return None
    return dict(zip(("job_id", "answer", "evaluated_at"), row))


def execute_request(database: Path, request: dict):
    """Short DB operation for ChatEvaluator; never waits on a browser."""
    from db.migrate import migrate_database

    database = database.resolve()
    if not database.is_file():
        raise FileNotFoundError(f"Database not found: {database}")
    migrate_database(database)
    with closing(sqlite3.connect(f"{database.as_uri()}?mode=rw", uri=True)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        if request['operation'] == 'find_job_id':
            return find_job_id(connection, request['url'])
        if request['operation'] == 'load':
            return load_evaluation(connection, request['job_id'])
        if request['operation'] == 'save':
            with connection:
                save_evaluation(connection, request['job_id'], request['answer'], request['evaluated_at'])
            return None
        raise ValueError(f"Unknown evaluation DB operation: {request['operation']}")


def main():
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    sys.stdin.reconfigure(encoding='utf-8')
    sys.stdout.reconfigure(encoding='utf-8')
    try:
        result = execute_request(Path(sys.argv[1]), json.load(sys.stdin))
        json.dump({'result': result}, sys.stdout, ensure_ascii=False)
    except Exception as error:
        json.dump({'error': str(error)}, sys.stdout, ensure_ascii=False)
        raise SystemExit(1)


if __name__ == '__main__':
    main()
