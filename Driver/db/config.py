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


def load_filter_switches(
    connection: sqlite3.Connection,
) -> dict[str, bool]:
    values = dict(FILTER_DEFAULTS)
    for config_name, value in connection.execute(
        "SELECT config_name, value FROM config"
    ).fetchall():
        name = str(config_name)
        if name in values:
            values[name] = str(value)
    return {
        name: value.strip().casefold() not in DISABLED_VALUES
        for name, value in values.items()
    }
