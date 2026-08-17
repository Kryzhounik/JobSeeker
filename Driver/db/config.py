"""Read runtime configuration stored in the application database."""

from __future__ import annotations

import sqlite3


COMPANY_FILTER = "company_filter"
TITLE_FILTER = "title_filter"
LANGUAGE_FILTER = "language_filter"
TECHNOLOGY_FILTER = "technology_filter"

FILTER_DEFAULTS = {
    COMPANY_FILTER: "1",
    TITLE_FILTER: "1",
    LANGUAGE_FILTER: "1",
    TECHNOLOGY_FILTER: "1",
}

DISABLED_VALUES = {"0", "false", "no", "off"}


def config_value(
    connection: sqlite3.Connection,
    config_name: str,
    default: str = "",
) -> str:
    row = connection.execute(
        "SELECT value FROM config WHERE config_name = ?",
        (config_name,),
    ).fetchone()
    return default if row is None else str(row[0])


def config_enabled(
    connection: sqlite3.Connection,
    config_name: str,
    default: bool = True,
) -> bool:
    fallback = "1" if default else "0"
    value = config_value(connection, config_name, fallback)
    return value.strip().casefold() not in DISABLED_VALUES
