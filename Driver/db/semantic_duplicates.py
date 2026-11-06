"""Saved collector snapshots and observational duplicate links."""
import sqlite3


def load_snapshot(connection: sqlite3.Connection, run_id: str, source: str, job_id: str) -> dict:
    row = connection.execute("""
        SELECT s.id, s.source_job_id, t.company_id, t.title, t.readable_text
        FROM source_jobs s JOIN source_job_texts t ON t.source_job_ref = s.id
        WHERE s.collection_run_id = ? AND s.source = ? AND s.source_job_id = ?
          AND s.processing_status = 'CLEANED'
    """, (run_id, source, job_id)).fetchone()
    if row is None:
        raise ValueError(f"Accepted vacancy snapshot missing: {source}:{job_id} in {run_id}")
    return dict(zip(('id', 'jobId', 'companyId', 'title', 'text'), row))


def save_link(connection: sqlite3.Connection, run_id: str, duplicate_id: int, original_id: int) -> None:
    if duplicate_id == original_id:
        raise ValueError("Semantic duplicate cannot refer to itself")
    updated = connection.execute("""
        UPDATE source_jobs SET semantic_duplicate_of = ?
        WHERE id = ? AND collection_run_id = ?
          AND EXISTS (SELECT 1 FROM source_jobs WHERE id = ? AND collection_run_id = ?)
    """, (original_id, duplicate_id, run_id, original_id, run_id))
    if updated.rowcount != 1:
        raise ValueError("Both semantic duplicate records must belong to the current run")
    connection.commit()
