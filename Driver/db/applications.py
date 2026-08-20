"""Application records linked to persisted vacancies."""

from __future__ import annotations

import sqlite3
from collections.abc import Iterable


DEFAULT_APPLICATION_STATUSES = ("Applied", "Refused", "Confirmed")


def create_for_source_urls(
    connection: sqlite3.Connection,
    source_urls: Iterable[str],
    applied_at: str,
) -> int:
    """Create one application per matching vacancy and return the inserted count."""
    urls = list(dict.fromkeys(url for url in source_urls if url))
    if not urls:
        return 0

    placeholders = ", ".join("?" for _url in urls)
    connection.execute(
        f"""
        INSERT OR IGNORE INTO applications (job_id, applied_at, status)
        SELECT id, ?, 'Applied'
        FROM jobs
        WHERE source_url IN ({placeholders})
        """,
        [applied_at, *urls],
    )
    return int(connection.execute("SELECT changes()").fetchone()[0])


def list_application_statuses(connection: sqlite3.Connection) -> tuple[str, ...]:
    rows = connection.execute(
        """
        SELECT code
        FROM application_statuses
        ORDER BY sort_order, code COLLATE NOCASE
        """
    ).fetchall()
    statuses = tuple(str(row[0]) for row in rows if row[0])
    return statuses or DEFAULT_APPLICATION_STATUSES


def list_applications(connection: sqlite3.Connection) -> list[sqlite3.Row]:
    return list(
        connection.execute(
            """
            SELECT
                a.id,
                a.job_id,
                a.applied_at,
                a.status,
                j.source_url,
                j.title,
                c.id AS company_id,
                coalesce(c.name, '') AS company
            FROM applications a
            JOIN jobs j ON j.id = a.job_id
            LEFT JOIN companies c ON c.id = j.company_id
            ORDER BY date(a.applied_at) DESC, a.id DESC
            """
        )
    )


def update_status(
    connection: sqlite3.Connection,
    application_id: int,
    status: str,
) -> None:
    result = connection.execute(
        """
        UPDATE applications
        SET status = ?,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = ?
        """,
        (status, application_id),
    )
    if result.rowcount != 1:
        raise KeyError(f"Application not found: {application_id}")
