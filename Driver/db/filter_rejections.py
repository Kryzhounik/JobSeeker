"""Persist structured content-filter rejection reasons."""

from __future__ import annotations

from collections.abc import Iterable
import sqlite3

from db.job_registry import validate_identity


def save_content_filter_rejection(
    connection: sqlite3.Connection,
    source: str,
    job_id: str,
    *,
    rule: str,
    matched_text: str,
    keyword_patterns: Iterable[tuple[str, str]],
) -> None:
    source, job_id = validate_identity(source, job_id)
    row = connection.execute(
        "SELECT id FROM source_jobs WHERE source = ? AND source_job_id = ?",
        (source, job_id),
    ).fetchone()
    if row is None:
        raise KeyError(f"Source job not found: {source}:{job_id}")

    source_job_ref = int(row[0])
    pairs = list(dict.fromkeys(keyword_patterns))
    if not rule.strip() or not matched_text.strip() or not pairs:
        raise ValueError("Rejected content filter result is incomplete")
    connection.executemany(
        """
        INSERT INTO content_filter_rejections (
            source_job_ref,
            rule,
            matched_text,
            matched_keyword,
            matched_pattern
        ) VALUES (?, ?, ?, ?, ?)
        """,
        [
            (source_job_ref, rule, matched_text, keyword, pattern)
            for keyword, pattern in pairs
        ],
    )
