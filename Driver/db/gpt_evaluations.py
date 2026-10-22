"""Last successful ChatGPT answer associated with a persisted vacancy."""

from __future__ import annotations

import sqlite3


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
