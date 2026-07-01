from __future__ import annotations

import argparse
import json
import sqlite3
import sys
from pathlib import Path
from typing import Any


LEVEL_RANKS = {
    "a1": 1,
    "a2": 2,
    "basic": 2,
    "beginner": 2,
    "junior": 2,
    "b1": 3,
    "intermediate": 3,
    "regular": 3,
    "mid": 3,
    "b2": 4,
    "upper-intermediate": 4,
    "advanced": 4,
    "senior": 4,
    "c1": 5,
    "expert": 5,
    "master": 5,
    "c2": 5,
    "fluent": 5,
    "native": 5,
}
TECH_EXPERIENCE_RANK_3 = (
    "experience required",
    "hands-on",
    "hands on",
    "commercial experience",
    "production experience",
    "solid experience",
    "strong",
)
TECH_MENTION_RANK_2 = ("listed", "mentioned", "required")
TECH_OPTIONAL_RANK_1 = (
    "nice to have",
    "nice-to-have",
    "will be a plus",
    "would be a plus",
    "plus",
)


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def clean(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def field(record: dict[str, Any], name: str, default: str = "") -> str:
    if name not in record:
        return default
    return clean(record.get(name))


def normalize_level(value: str | None) -> str:
    return clean(value)


def level_rank(level: str | None) -> int:
    text = normalize_level(level)
    if not text:
        return 1

    normalized = text.lower().replace("_", "-").replace("/", " ")
    for token, rank in LEVEL_RANKS.items():
        if token in normalized:
            return rank
    return 1


def technology_level_rank(level: str | None, requirement_type: str) -> int:
    if requirement_type == "nice_to_have":
        return 1

    text = normalize_level(level)
    if not text:
        return 2

    normalized = text.lower().replace("_", "-").replace("/", " ")
    if any(token in normalized for token in TECH_OPTIONAL_RANK_1):
        return 1
    for token, rank in LEVEL_RANKS.items():
        if token in normalized:
            return rank
    if any(token in normalized for token in TECH_EXPERIENCE_RANK_3):
        return 3
    if any(token in normalized for token in TECH_MENTION_RANK_2):
        return 2
    return 2


def requirement_type(value: str | None) -> str:
    normalized = clean(value, "required").lower()
    if normalized in {"opt", "optional", "nice_to_have", "nice to have"}:
        return "nice_to_have"
    return "required"


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    ensure_existing_schema(connection)
    connection.executescript(schema_path.read_text(encoding="utf-8"))


def ensure_existing_schema(connection: sqlite3.Connection) -> None:
    jobs_exists = connection.execute(
        """
        SELECT 1
        FROM sqlite_master
        WHERE type = 'table' AND name = 'jobs'
        """
    ).fetchone()
    if not jobs_exists:
        return

    columns = {
        row[1]
        for row in connection.execute("PRAGMA table_info(jobs)").fetchall()
    }
    if "remote_scope" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN remote_scope TEXT NOT NULL DEFAULT 'unknown'"
        )
    if "relocation" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN relocation TEXT NOT NULL DEFAULT 'NO'"
        )
    if "valuation" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN valuation INTEGER NOT NULL DEFAULT 0"
        )
    if "fitability_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN fitability_percent INTEGER NOT NULL DEFAULT 100"
        )


def get_or_create_id(
    connection: sqlite3.Connection,
    table: str,
    name: str,
) -> int:
    if table not in {"languages", "technologies"}:
        raise ValueError(f"Unsupported dictionary table: {table}")

    connection.execute(f"INSERT OR IGNORE INTO {table} (name) VALUES (?)", (name,))
    row = connection.execute(
        f"SELECT id FROM {table} WHERE name = ?",
        (name,),
    ).fetchone()
    return int(row[0])


def write_job(connection: sqlite3.Connection, record: dict[str, Any]) -> int:
    source_url = clean(record.get("source_url"))
    title = clean(record.get("title"))
    if not source_url:
        raise ValueError("Job record must include source_url.")
    if not title:
        raise ValueError("Job record must include title.")

    connection.execute(
        """
        INSERT INTO jobs (
            source,
            source_url,
            title,
            company,
            location,
            remote_type,
            remote_scope,
            relocation,
            valuation,
            seniority,
            role,
            salary,
            summary,
            pros,
            cons,
            notes,
            added_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(source_url) DO UPDATE SET
            title = excluded.title,
            company = excluded.company,
            location = excluded.location,
            remote_type = excluded.remote_type,
            remote_scope = excluded.remote_scope,
            relocation = excluded.relocation,
            valuation = excluded.valuation,
            seniority = excluded.seniority,
            role = excluded.role,
            salary = excluded.salary,
            summary = excluded.summary,
            pros = excluded.pros,
            cons = excluded.cons,
            notes = excluded.notes,
            updated_at = CURRENT_TIMESTAMP
        """,
        (
            clean(record.get("source"), "justjoin"),
            source_url,
            title,
            clean(record.get("company")),
            clean(record.get("location")),
            clean(record.get("remote_type"), "unknown"),
            field(record, "remote_scope", "unknown"),
            field(record, "relocation", "NO"),
            int(record.get("valuation") or 0),
            clean(record.get("seniority"), "unknown"),
            clean(record.get("role"), "unknown"),
            clean(record.get("salary"), "unknown"),
            clean(record.get("summary")),
            clean(record.get("pros")),
            clean(record.get("cons")),
            clean(record.get("notes")),
            clean(record.get("added_at")),
        ),
    )
    job_id = int(
        connection.execute(
            "SELECT id FROM jobs WHERE source_url = ?",
            (source_url,),
        ).fetchone()[0]
    )

    replace_languages(connection, job_id, record.get("languages", []))
    replace_technologies(connection, job_id, record.get("technologies", []))
    return job_id


def replace_languages(
    connection: sqlite3.Connection,
    job_id: int,
    languages: list[dict[str, Any]],
) -> None:
    connection.execute("DELETE FROM job_languages WHERE job_id = ?", (job_id,))

    primary_language_id = None
    primary_rank = -1
    for language in languages:
        name = clean(language.get("name"))
        if not name:
            continue

        level = normalize_level(language.get("level"))
        rank = int(language.get("level_rank") or level_rank(level))
        language_id = get_or_create_id(connection, "languages", name)
        connection.execute(
            """
            INSERT INTO job_languages (
                job_id,
                language_id,
                level,
                level_rank,
                raw_value
            )
            VALUES (?, ?, ?, ?, ?)
            """,
            (job_id, language_id, level, rank, f"{name}: {level}" if level else name),
        )

        if rank > primary_rank:
            primary_language_id = language_id
            primary_rank = rank

    connection.execute(
        "UPDATE jobs SET primary_language_id = ? WHERE id = ?",
        (primary_language_id, job_id),
    )


def replace_technologies(
    connection: sqlite3.Connection,
    job_id: int,
    technologies: list[dict[str, Any]],
) -> None:
    connection.execute("DELETE FROM job_technologies WHERE job_id = ?", (job_id,))

    for technology in technologies:
        name = clean(technology.get("name"))
        if not name:
            continue

        req_type = requirement_type(technology.get("requirement"))
        level = normalize_level(technology.get("level"))
        technology_id = get_or_create_id(connection, "technologies", name)
        raw_value = clean(technology.get("raw_value"))
        if not raw_value:
            raw_value = f"{name}: {level}" if level else name

        connection.execute(
            """
            INSERT INTO job_technologies (
                job_id,
                technology_id,
                requirement_type,
                level,
                level_rank,
                raw_value
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                job_id,
                technology_id,
                req_type,
                level,
                int(technology.get("level_rank") or technology_level_rank(level, req_type)),
                raw_value,
            ),
        )


def load_record(input_path: str) -> dict[str, Any]:
    if input_path == "-":
        return json.load(sys.stdin)
    with Path(input_path).open("r", encoding="utf-8") as file:
        return json.load(file)


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser(description="Write analyzed job JSON to SQLite.")
    parser.add_argument("--input", "-i", default="-")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "analyzer" / "db" / "schema.sql"))
    args = parser.parse_args()

    record = load_record(args.input)
    with sqlite3.connect(args.db) as connection:
        apply_schema(connection, Path(args.schema))
        job_id = write_job(connection, record)
    print(f"wrote job_id={job_id} {record['source_url']}")


if __name__ == "__main__":
    main()
