"""Job interest scoring.

This answers "how interesting is this vacancy to the candidate?" and is separate
from candidate fit. It scores remote, relocation, and tech bonuses.
"""

from __future__ import annotations

import argparse
import configparser
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from db.migrate import migrate_database
from scoring.candidate_fit.filter import location_contains
from scoring.candidate_fit.filter import location_tokens


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


def covered_location_score(
    config: configparser.ConfigParser,
    section: str,
    value: str,
    skip_keys: set[str] | None = None,
) -> int:
    skip = {key.lower() for key in (skip_keys or set())}
    value_tokens = location_tokens(value)
    best = 0

    if not config.has_section(section):
        return 0

    for key, raw_score in config.items(section):
        if key.lower() in skip:
            continue

        key_tokens = location_tokens(key)
        if not any(
            location_contains(value_token, key_token)
            for value_token in value_tokens
            for key_token in key_tokens
        ):
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

    if "worldwide" in location_tokens(scope):
        return int_value(config, "remote", "worldwide", 1000)

    covered_score = covered_location_score(
        config,
        "remote",
        scope,
        skip_keys={"worldwide"},
    )
    if covered_score:
        return covered_score

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


def job_interest_for_json(
    config: configparser.ConfigParser,
    record: dict[str, Any],
) -> int:
    """Calculate job-interest for one analyzed/scored JSON record."""
    technologies = [
        str(technology.get("name") or "")
        for technology in record.get("technologies", [])
        if isinstance(technology, dict)
    ]
    score = (
        remote_score(
            config,
            str(record.get("remote_type") or ""),
            str(record.get("remote_scope") or ""),
        )
        + relocation_score(config, str(record.get("relocation") or ""))
        + technology_score(config, technologies)
    )
    candidate_fit = int(record.get("candidate_fit_percent") or 0)
    if score == 0 and candidate_fit > 0:
        score = 1
    return score


def score_job_json(
    record: dict[str, Any],
    config: configparser.ConfigParser,
) -> dict[str, Any]:
    """Add job_interest to a candidate-fit-scored JSON record."""
    record["job_interest"] = job_interest_for_json(config, record)
    return record


def json_paths(input_path: Path) -> list[Path]:
    if input_path.is_file():
        return [input_path]
    return sorted(input_path.glob("*.json"))


def score_json_files(
    input_path: Path,
    config_path: Path,
    output_dir: Path | None = None,
) -> list[tuple[Path, int, str]]:
    config = load_config(config_path)
    results: list[tuple[Path, int, str]] = []

    for path in json_paths(input_path):
        record = json.loads(path.read_text(encoding="utf-8"))
        if not isinstance(record, dict):
            raise ValueError(f"{path} does not contain a JSON object")

        score_job_json(record, config)
        output_path = path
        if output_dir is not None:
            output_dir.mkdir(parents=True, exist_ok=True)
            output_path = output_dir / path.name

        output_path.write_text(
            json.dumps(record, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        results.append(
            (output_path, int(record["job_interest"]), str(record.get("title") or ""))
        )

    return results


def update_job_interest(
    db_path: Path,
    schema_path: Path,
    config_path: Path,
) -> list[tuple[int, int, int, str, str]]:
    config = load_config(config_path)
    migrate_database(db_path=db_path, schema_path=schema_path)

    with sqlite3.connect(db_path) as connection:
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys = ON")
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
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "db" / "schema.sql"))
    parser.add_argument(
        "--input",
        help=(
            "Scored JSON file or directory. When set, calculate job_interest "
            "in JSON before db/save.py."
        ),
    )
    parser.add_argument(
        "--output-dir",
        help=(
            "Optional output directory. Use this when converting analyzed JSON "
            "into scored JSON without rewriting Data/analyzed."
        ),
    )
    parser.add_argument(
        "--config",
        default=str(root / "scoring" / "job_interest" / "config" / "interest.ini"),
    )
    args = parser.parse_args()

    if args.input:
        results = score_json_files(
            input_path=Path(args.input),
            config_path=Path(args.config),
            output_dir=Path(args.output_dir) if args.output_dir else None,
        )
        for path, score, title in results:
            print(f"{path}: job_interest={score} {title}")
        print(f"processed {len(results)} analyzed JSON records")
        return

    updates = update_job_interest(
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        config_path=Path(args.config),
    )
    for job_id, score, candidate_fit, title, reason in updates:
        print(f"{job_id}: {score} [{candidate_fit}%] {title} ({reason})")


if __name__ == "__main__":
    main()
