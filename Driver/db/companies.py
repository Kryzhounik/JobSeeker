"""Company dictionary access shared by persistence and filters."""

from __future__ import annotations

import sqlite3
from typing import Any


def normalize_company_name(value: Any) -> str:
    if value is None:
        return ""
    return " ".join(str(value).split())


def normalize_linkedin_id(value: Any) -> str | None:
    if value is None:
        return None
    linkedin_id = str(value).strip()
    return linkedin_id or None


def create_company(
    connection: sqlite3.Connection,
    name: Any,
    linkedin_id: Any = None,
) -> int:
    normalized_name = normalize_company_name(name)
    if not normalized_name:
        raise ValueError("Company name is required.")

    result = connection.execute(
        """
        INSERT INTO companies (name, linkedin_id)
        VALUES (?, ?)
        """,
        (normalized_name, normalize_linkedin_id(linkedin_id)),
    )
    return int(result.lastrowid)


def get_or_create_company(
    connection: sqlite3.Connection,
    company: Any,
) -> int | None:
    name = normalize_company_name(company)
    if not name:
        return None

    connection.execute(
        "INSERT OR IGNORE INTO companies (name) VALUES (?)",
        (name,),
    )
    row = connection.execute(
        "SELECT id FROM companies WHERE name = ? COLLATE NOCASE",
        (name,),
    ).fetchone()
    if row is None:
        raise RuntimeError(f"Could not resolve company: {name}")
    return int(row[0])


def is_company_blacklisted(
    connection: sqlite3.Connection,
    company: Any,
) -> bool:
    name = normalize_company_name(company)
    if not name:
        return False
    return (
        connection.execute(
            """
            SELECT 1
            FROM companies
            WHERE name = ? COLLATE NOCASE
                AND blacklisted = 1
            """,
            (name,),
        ).fetchone()
        is not None
    )
