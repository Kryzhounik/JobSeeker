"""Compact run/page diagnostics for the Java LinkedIn collector."""

from __future__ import annotations

from contextlib import closing
import json
from pathlib import Path
import sqlite3
from typing import Any


def start_collection_run(
    run_id: str,
    config: dict[str, Any],
    db_path: Path,
) -> None:
    config_json = json.dumps(
        config,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(
            """
            INSERT INTO linkedin_collection_runs (
                run_id,
                status,
                config_json
            )
            VALUES (?, 'running', ?)
            ON CONFLICT(run_id) DO UPDATE SET
                started_at = CURRENT_TIMESTAMP,
                finished_at = NULL,
                status = 'running',
                stop_reason = '',
                accepted_count = 0,
                config_json = excluded.config_json,
                message = ''
            """,
            (run_id, config_json),
        )
        connection.execute(
            "DELETE FROM linkedin_collection_pages WHERE run_id = ?",
            (run_id,),
        )
        connection.commit()


def record_collection_page(
    run_id: str,
    page: dict[str, Any],
    db_path: Path,
) -> None:
    values = (
        run_id,
        _integer(page, "sequence"),
        _text(page, "label"),
        _text(page, "search"),
        _integer(page, "start"),
        _text(page, "requested_url"),
        _text(page, "actual_url"),
        _text(page, "layout"),
        _integer(page, "expected_count"),
        _nullable_integer(page, "total_results"),
        _integer(page, "materialized_count"),
        _integer(page, "new_count"),
        _integer(page, "target_new_count"),
        _integer(page, "scroll_iterations"),
        _integer(page, "unchanged_iterations"),
        _text(page, "card_ids_hash"),
        _boolean(page, "terminal"),
        _text(page, "terminal_reason"),
        _integer(page, "next_count"),
        _boolean(page, "next_visible"),
        _boolean(page, "next_disabled"),
        _text(page, "next_aria_disabled"),
        _text(page, "next_label")[:160],
        _text(page, "stop_reason"),
    )
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.execute(
            """
            INSERT INTO linkedin_collection_pages (
                run_id,
                sequence_no,
                label,
                search,
                start,
                requested_url,
                actual_url,
                layout,
                expected_count,
                total_results,
                materialized_count,
                new_count,
                target_new_count,
                scroll_iterations,
                unchanged_iterations,
                card_ids_hash,
                terminal,
                terminal_reason,
                next_count,
                next_visible,
                next_disabled,
                next_aria_disabled,
                next_label,
                stop_reason
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(run_id, sequence_no) DO UPDATE SET
                label = excluded.label,
                search = excluded.search,
                start = excluded.start,
                requested_url = excluded.requested_url,
                actual_url = excluded.actual_url,
                layout = excluded.layout,
                expected_count = excluded.expected_count,
                total_results = excluded.total_results,
                materialized_count = excluded.materialized_count,
                new_count = excluded.new_count,
                target_new_count = excluded.target_new_count,
                scroll_iterations = excluded.scroll_iterations,
                unchanged_iterations = excluded.unchanged_iterations,
                card_ids_hash = excluded.card_ids_hash,
                terminal = excluded.terminal,
                terminal_reason = excluded.terminal_reason,
                next_count = excluded.next_count,
                next_visible = excluded.next_visible,
                next_disabled = excluded.next_disabled,
                next_aria_disabled = excluded.next_aria_disabled,
                next_label = excluded.next_label,
                stop_reason = excluded.stop_reason
            """,
            values,
        )
        connection.commit()


def finish_collection_run(
    run_id: str,
    status: str,
    stop_reason: str,
    accepted_count: int,
    message: str,
    db_path: Path,
) -> None:
    with closing(sqlite3.connect(db_path)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        cursor = connection.execute(
            """
            UPDATE linkedin_collection_runs
            SET
                finished_at = CURRENT_TIMESTAMP,
                status = ?,
                stop_reason = ?,
                accepted_count = ?,
                message = ?
            WHERE run_id = ?
            """,
            (
                status.strip(),
                stop_reason.strip(),
                int(accepted_count),
                message.strip(),
                run_id,
            ),
        )
        if cursor.rowcount != 1:
            raise ValueError(f"Unknown LinkedIn collection run: {run_id}")
        connection.commit()


def _text(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    return "" if value is None else str(value).strip()


def _integer(payload: dict[str, Any], key: str) -> int:
    value = payload.get(key)
    return 0 if value is None or value == "" else int(value)


def _nullable_integer(payload: dict[str, Any], key: str) -> int | None:
    value = payload.get(key)
    return None if value is None or value == "" else int(value)


def _boolean(payload: dict[str, Any], key: str) -> int:
    return 1 if payload.get(key) is True else 0
