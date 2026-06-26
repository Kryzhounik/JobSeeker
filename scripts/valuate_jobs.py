from __future__ import annotations

import argparse
import configparser
import sqlite3
from pathlib import Path


NO_VALUES = {"", "no", "none", "unknown", "n/a", "-"}


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_config(path: Path) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path, encoding="utf-8")
    return config


def clean(value: object) -> str:
    return str(value or "").strip()


def normalized(value: object) -> str:
    return clean(value).lower()


def int_value(config: configparser.ConfigParser, section: str, key: str, default: int) -> int:
    try:
        return config.getint(section, key)
    except (configparser.Error, ValueError):
        return default


def best_match_score(
    config: configparser.ConfigParser,
    section: str,
    value: str,
    skip_keys: set[str] | None = None,
) -> int:
    skip = {key.lower() for key in (skip_keys or set())}
    haystack = normalized(value)
    best = 0

    if not config.has_section(section):
        return 0

    for key, raw_score in config.items(section):
        if key.lower() in skip:
            continue

        needle = key.lower()
        if not needle or needle not in haystack:
            continue

        try:
            best = max(best, int(raw_score))
        except ValueError:
            continue

    return best


def remote_score(config: configparser.ConfigParser, remote_type: str, remote_scope: str) -> int:
    if normalized(remote_type) != "remote":
        return 0

    scope = clean(remote_scope)
    if normalized(scope) in NO_VALUES:
        return 0

    if normalized(scope) == "worldwide":
        return int_value(config, "remote", "worldwide", 1000)

    return best_match_score(config, "remote", scope, skip_keys={"worldwide"})


def relocation_score(config: configparser.ConfigParser, relocation: str) -> int:
    destination = clean(relocation)
    if normalized(destination) in NO_VALUES:
        return 0

    base = int_value(config, "relocation", "base", 100)
    matched = best_match_score(config, "relocation", destination, skip_keys={"base"})
    return base + matched


def tech_score(config: configparser.ConfigParser) -> int:
    return int_value(config, "tech", "default", 0)


def valuation(config: configparser.ConfigParser, row: sqlite3.Row) -> int:
    return (
        remote_score(config, row["remote_type"], row["remote_scope"])
        + relocation_score(config, row["relocation"])
        + tech_score(config)
    )


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    connection.executescript(
        """
        DROP VIEW IF EXISTS job_technology_display;
        DROP VIEW IF EXISTS job_language_list;
        DROP VIEW IF EXISTS job_technology_list;
        DROP VIEW IF EXISTS job_list;
        DROP VIEW IF EXISTS job_view;
        DROP INDEX IF EXISTS idx_jobs_status;
        """
    )
    ensure_valuation_column(connection)
    drop_legacy_status(connection)
    connection.executescript(schema_path.read_text(encoding="utf-8"))


def ensure_valuation_column(connection: sqlite3.Connection) -> None:
    columns = table_columns(connection, "jobs")
    if "valuation" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN valuation INTEGER NOT NULL DEFAULT 0"
        )


def drop_legacy_status(connection: sqlite3.Connection) -> None:
    columns = table_columns(connection, "jobs")
    if "status" not in columns:
        return

    try:
        connection.execute("ALTER TABLE jobs DROP COLUMN status")
    except sqlite3.OperationalError:
        # Older SQLite builds may not support DROP COLUMN. The schema/view no
        # longer uses status, so leaving the legacy column hidden is harmless.
        pass


def table_columns(connection: sqlite3.Connection, table: str) -> set[str]:
    return {
        row[1]
        for row in connection.execute(f"PRAGMA table_info({table})").fetchall()
    }


def update_valuations(
    db_path: Path,
    schema_path: Path,
    config_path: Path,
) -> list[tuple[int, int, str]]:
    config = load_config(config_path)

    with sqlite3.connect(db_path) as connection:
        connection.row_factory = sqlite3.Row
        apply_schema(connection, schema_path)
        rows = connection.execute(
            """
            SELECT id, title, remote_type, remote_scope, relocation
            FROM jobs
            ORDER BY id
            """
        ).fetchall()

        updates: list[tuple[int, int, str]] = []
        for row in rows:
            score = valuation(config, row)
            connection.execute(
                "UPDATE jobs SET valuation = ? WHERE id = ?",
                (score, row["id"]),
            )
            updates.append((row["id"], score, row["title"]))

        connection.commit()
        return updates


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser(description="Recalculate job valuations.")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "db" / "schema.sql"))
    parser.add_argument("--config", default=str(root / "config" / "valuation.ini"))
    args = parser.parse_args()

    updates = update_valuations(
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        config_path=Path(args.config),
    )
    for job_id, score, title in updates:
        print(f"{job_id}: {score} {title}")


if __name__ == "__main__":
    main()
