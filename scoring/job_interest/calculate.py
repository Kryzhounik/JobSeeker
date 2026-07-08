"""Job interest scoring.

This answers "how interesting is this vacancy to the candidate?" and is separate
from candidate fit. It scores remote, relocation, and tech bonuses.
"""

from __future__ import annotations

import argparse
import configparser
import sqlite3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from db.job_mapper import apply_schema


NO_VALUES = {"", "no", "none", "unknown", "n/a", "-"}


def project_root() -> Path:
    return ROOT


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


def technology_score(config: configparser.ConfigParser, technologies: list[str]) -> int:
    score = int_value(config, "tech", "default", 0)
    if not config.has_section("tech.keywords"):
        return score

    for technology in technologies:
        value = normalized(technology)
        for keyword, raw_score in config.items("tech.keywords"):
            if keyword.lower() not in value:
                continue
            try:
                score += int(raw_score)
            except ValueError:
                continue
            break

    return min(score, int_value(config, "tech", "max", 99))


def job_interest(
    config: configparser.ConfigParser,
    row: sqlite3.Row,
    technologies: list[str],
) -> int:
    return (
        remote_score(config, row["remote_type"], row["remote_scope"])
        + relocation_score(config, row["relocation"])
        + technology_score(config, technologies)
    )


def update_job_interest(
    db_path: Path,
    schema_path: Path,
    config_path: Path,
) -> list[tuple[int, int, int, str, str]]:
    config = load_config(config_path)

    with sqlite3.connect(db_path) as connection:
        connection.row_factory = sqlite3.Row
        apply_schema(connection, schema_path)
        rows = connection.execute(
            """
            SELECT
                id,
                title,
                remote_type,
                remote_scope,
                relocation,
                candidate_fit_percent
            FROM jobs
            ORDER BY id
            """
        ).fetchall()

        updates: list[tuple[int, int, int, str, str]] = []
        for row in rows:
            technologies = [
                tech_row[0]
                for tech_row in connection.execute(
                    """
                    SELECT t.name
                    FROM job_technologies jt
                    JOIN technologies t ON t.id = jt.technology_id
                    WHERE jt.job_id = ?
                    """,
                    (row["id"],),
                ).fetchall()
            ]
            score = job_interest(config, row, technologies)
            candidate_fit = int(row["candidate_fit_percent"] or 0)
            if score == 0 and candidate_fit > 0:
                score = 1
            connection.execute(
                """
                UPDATE jobs
                SET job_interest = ?
                WHERE id = ?
                """,
                (score, row["id"]),
            )
            updates.append(
                (
                    row["id"],
                    score,
                    candidate_fit,
                    row["title"],
                    "candidate fit unchanged",
                )
            )

        connection.commit()
        return updates


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    root = project_root()
    parser = argparse.ArgumentParser(description="Recalculate job interest.")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "db" / "schema.sql"))
    parser.add_argument(
        "--config",
        default=str(root / "scoring" / "job_interest" / "config" / "interest.ini"),
    )
    args = parser.parse_args()

    updates = update_job_interest(
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        config_path=Path(args.config),
    )
    for job_id, score, candidate_fit, title, reason in updates:
        print(f"{job_id}: {score} [{candidate_fit}%] {title} ({reason})")


if __name__ == "__main__":
    main()
