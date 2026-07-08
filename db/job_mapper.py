"""Canonical mapper between analyzed job JSON and SQLite.

All code that writes jobs to the database or reconstructs job JSON from the
database should use this module. Do not hand-roll SELECT-to-JSON mapping in
agent-specific code.
"""

from __future__ import annotations

import sqlite3
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

JOB_COLUMNS = (
    "id",
    "source",
    "source_url",
    "title",
    "company",
    "location",
    "remote_type",
    "remote_scope",
    "relocation",
    "job_interest",
    "candidate_fit_percent",
    "seniority",
    "role",
    "salary",
    "summary",
    "pros",
    "cons",
    "notes",
    "added_at",
    "primary_language_id",
)


def clean(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def field(record: dict[str, Any], name: str, default: str = "") -> str:
    if name not in record:
        return default
    return clean(record.get(name))


def int_field(record: dict[str, Any], name: str, default: int = 0) -> int:
    value = record.get(name)
    if value is None or value == "":
        return default
    return int(value)


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
    connection.executescript(
        """
        DROP VIEW IF EXISTS job_technology_display;
        DROP VIEW IF EXISTS job_language_list;
        DROP VIEW IF EXISTS job_technology_list;
        DROP VIEW IF EXISTS job_list;
        DROP VIEW IF EXISTS job_view;
        DROP INDEX IF EXISTS idx_jobs_valuation;
        DROP INDEX IF EXISTS idx_jobs_fitability;
        """
    )
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
    if "valuation" in columns and "job_interest" not in columns:
        connection.execute("ALTER TABLE jobs RENAME COLUMN valuation TO job_interest")
        columns.remove("valuation")
        columns.add("job_interest")
    if "fitability_percent" in columns and "candidate_fit_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs RENAME COLUMN fitability_percent TO candidate_fit_percent"
        )
        columns.remove("fitability_percent")
        columns.add("candidate_fit_percent")

    if "job_interest" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN job_interest INTEGER NOT NULL DEFAULT 0"
        )
    if "candidate_fit_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN candidate_fit_percent INTEGER NOT NULL DEFAULT 100"
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


def save_job_json(connection: sqlite3.Connection, record: dict[str, Any]) -> int:
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
            job_interest,
            candidate_fit_percent,
            seniority,
            role,
            salary,
            summary,
            pros,
            cons,
            notes,
            added_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(source_url) DO UPDATE SET
            title = excluded.title,
            company = excluded.company,
            location = excluded.location,
            remote_type = excluded.remote_type,
            remote_scope = excluded.remote_scope,
            relocation = excluded.relocation,
            job_interest = excluded.job_interest,
            candidate_fit_percent = excluded.candidate_fit_percent,
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
            int_field(record, "job_interest", int_field(record, "valuation", 0)),
            int_field(
                record,
                "candidate_fit_percent",
                int_field(record, "fitability_percent", 100),
            ),
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


def load_job_json(
    connection: sqlite3.Connection,
    *,
    job_id: int | None = None,
    source_url: str | None = None,
) -> dict[str, Any]:
    if job_id is None and not source_url:
        raise ValueError("Pass job_id or source_url.")

    where = "id = ?"
    value: Any = job_id
    if source_url:
        where = "source_url = ?"
        value = source_url

    row = connection.execute(
        f"""
        SELECT {", ".join(JOB_COLUMNS)}
        FROM jobs
        WHERE {where}
        """,
        (value,),
    ).fetchone()
    if row is None:
        raise KeyError(f"Job not found: {value}")

    job = dict(zip(JOB_COLUMNS, row))
    db_id = int(job.pop("id"))
    primary_language_id = job.pop("primary_language_id")

    record: dict[str, Any] = {
        "source": clean(job["source"], "justjoin"),
        "source_url": clean(job["source_url"]),
        "added_at": clean(job["added_at"]),
        "salary": clean(job["salary"], "unknown"),
        "relocation": clean(job["relocation"], "NO"),
        "job_interest": int(job["job_interest"] or 0),
        "candidate_fit_percent": int(job["candidate_fit_percent"] or 0),
        "pros": clean(job["pros"]),
        "cons": clean(job["cons"]),
        "notes": clean(job["notes"]),
        "languages": load_languages(connection, db_id, primary_language_id),
        "technologies": load_technologies(connection, db_id),
        "title": clean(job["title"]),
        "company": clean(job["company"]),
        "location": clean(job["location"]),
        "remote_type": clean(job["remote_type"], "unknown"),
        "remote_scope": clean(job["remote_scope"], "unknown"),
        "seniority": clean(job["seniority"], "unknown"),
        "role": clean(job["role"], "unknown"),
        "summary": clean(job["summary"]),
    }
    return record


def load_languages(
    connection: sqlite3.Connection,
    job_id: int,
    primary_language_id: int | None,
) -> list[dict[str, Any]]:
    rows = connection.execute(
        """
        SELECT
            l.name,
            jl.level,
            jl.level_rank,
            jl.raw_value
        FROM job_languages jl
        JOIN languages l ON l.id = jl.language_id
        WHERE jl.job_id = ?
        ORDER BY
            CASE WHEN jl.language_id = ? THEN 0 ELSE 1 END,
            jl.level_rank DESC,
            l.name COLLATE NOCASE
        """,
        (job_id, primary_language_id),
    ).fetchall()
    return [
        {
            "name": clean(row[0]),
            "level": clean(row[1]),
            "level_rank": int(row[2] or 0),
            "raw_value": clean(row[3]),
        }
        for row in rows
    ]


def load_technologies(
    connection: sqlite3.Connection,
    job_id: int,
) -> list[dict[str, Any]]:
    rows = connection.execute(
        """
        SELECT
            t.name,
            jt.requirement_type,
            jt.level,
            jt.level_rank,
            jt.raw_value
        FROM job_technologies jt
        JOIN technologies t ON t.id = jt.technology_id
        WHERE jt.job_id = ?
        ORDER BY
            CASE jt.requirement_type
                WHEN 'required' THEN 1
                WHEN 'nice_to_have' THEN 2
                ELSE 9
            END,
            jt.level_rank DESC,
            t.name COLLATE NOCASE
        """,
        (job_id,),
    ).fetchall()
    return [
        {
            "name": clean(row[0]),
            "requirement": clean(row[1], "required"),
            "level": clean(row[2]),
            "level_rank": int(row[3] or 0),
            "raw_value": clean(row[4]),
        }
        for row in rows
    ]


# Backward-compatible alias for older scripts. New code should use save_job_json.
write_job = save_job_json
