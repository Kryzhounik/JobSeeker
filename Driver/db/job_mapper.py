"""Canonical mapper between analyzed job JSON and SQLite.

All code that writes jobs to the database or reconstructs job JSON from the
database should use this module. Do not hand-roll SELECT-to-JSON mapping in
agent-specific code.
"""

from __future__ import annotations

import sqlite3
from pathlib import Path
from typing import Any, Iterable

from db.job_registry import mark_url_status
from db.job_registry import source_job_id


JOB_COLUMNS = (
    "id",
    "source",
    "source_url",
    "status",
    "title",
    "company",
    "location",
    "remote_type",
    "remote_scope",
    "relocation",
    "job_interest",
    "candidate_fit_percent",
    "candidate_fit_reason_code",
    "candidate_fit_reason",
    "seniority",
    "role",
    "salary",
    "summary",
    "notes",
    "added_at",
    "primary_language_id",
)

DEFAULT_JOB_STATUSES = ("New", "Checked", "Postponed", "Applied", "Closed")
CANDIDATE_FIT_REASON_CODES = (
    "undefined",
    "ok",
    "lang",
    "loc",
    "tech",
    "role_mismatch",
    "skill_mismatch",
)
TECHNOLOGY_REQUIREMENT_TYPES = (
    "core",
    "required",
    "important",
    "desired",
    "nice_to_have",
)


def clean(value: Any, default: str = "") -> str:
    if value is None:
        return default
    text = str(value).strip()
    return text if text else default


def job_status(value: Any) -> str:
    return clean(value, "New")


def candidate_fit_reason_code(value: Any) -> str:
    code = clean(value, "undefined")
    if code not in CANDIDATE_FIT_REASON_CODES:
        raise ValueError(
            "Unsupported candidate-fit reason code: "
            f"{code!r}. Expected one of: "
            f"{', '.join(CANDIDATE_FIT_REASON_CODES)}"
        )
    return code


def required_text(record: dict[str, Any], name: str) -> str:
    value = clean(record[name])
    if not value:
        raise ValueError(f"Required text field is empty: {name}")
    return value


def required_int(record: dict[str, Any], name: str) -> int:
    value = record[name]
    if value is None or value == "":
        raise ValueError(f"Required integer field is empty: {name}")
    return int(value)


def normalize_level(value: str | None) -> str:
    return clean(value)


def requirement_type(value: str | None) -> str:
    normalized = clean(value, "required").lower()
    if normalized not in TECHNOLOGY_REQUIREMENT_TYPES:
        raise ValueError(f"Unsupported technology requirement: {value}")
    return normalized


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    connection.execute("PRAGMA foreign_keys = ON")
    schema_sql = schema_path.read_text(encoding="utf-8")
    connection.executescript(
        """
        DROP VIEW IF EXISTS job_technology_display;
        DROP VIEW IF EXISTS job_language_list;
        DROP VIEW IF EXISTS job_technology_list;
        DROP VIEW IF EXISTS job_list;
        DROP VIEW IF EXISTS job_view;
        """
    )
    ensure_existing_schema(connection, schema_sql)
    connection.executescript(schema_sql)


def existing_source_urls(connection: sqlite3.Connection) -> set[str]:
    return {
        row[0]
        for row in connection.execute(
            """
            SELECT text.source_url
            FROM jobs job
            JOIN source_job_texts text
                ON text.source_job_ref = job.source_job_ref
            WHERE length(trim(text.source_url)) > 0
            """
        ).fetchall()
    }


def delete_jobs(
    connection: sqlite3.Connection,
    job_ids: Iterable[int],
) -> list[int]:
    """Delete jobs and their source registry rows in the current transaction."""
    requested_ids = list(dict.fromkeys(int(job_id) for job_id in job_ids))
    existing: list[tuple[int, int]] = []
    for job_id in requested_ids:
        row = connection.execute(
            "SELECT id, source_job_ref FROM jobs WHERE id = ?",
            (job_id,),
        ).fetchone()
        if row is not None:
            existing.append((int(row[0]), int(row[1])))

    connection.executemany(
        "DELETE FROM jobs WHERE id = ?",
        [(job_id,) for job_id, _source_job_ref in existing],
    )
    connection.executemany(
        "DELETE FROM source_jobs WHERE id = ?",
        [(source_job_ref,) for _job_id, source_job_ref in existing],
    )
    return [job_id for job_id, _source_job_ref in existing]


def delete_source_jobs(
    connection: sqlite3.Connection,
    source_job_refs: Iterable[int],
) -> list[int]:
    """Delete collected vacancies and any analyzed rows derived from them."""
    requested_refs = list(dict.fromkeys(int(ref) for ref in source_job_refs))
    existing_refs: list[int] = []
    for source_job_ref in requested_refs:
        row = connection.execute(
            "SELECT id FROM source_jobs WHERE id = ?",
            (source_job_ref,),
        ).fetchone()
        if row is not None:
            existing_refs.append(int(row[0]))

    connection.executemany(
        "DELETE FROM jobs WHERE source_job_ref = ?",
        [(source_job_ref,) for source_job_ref in existing_refs],
    )
    connection.executemany(
        "DELETE FROM source_jobs WHERE id = ?",
        [(source_job_ref,) for source_job_ref in existing_refs],
    )
    return existing_refs


def ensure_existing_schema(connection: sqlite3.Connection, schema_sql: str) -> None:
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
    if "job_interest" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN job_interest INTEGER NOT NULL DEFAULT 0"
        )
    if "candidate_fit_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN candidate_fit_percent INTEGER NOT NULL DEFAULT 100"
        )
    if "candidate_fit_reason_code" not in columns:
        connection.execute(
            """
            ALTER TABLE jobs
            ADD COLUMN candidate_fit_reason_code TEXT NOT NULL DEFAULT 'undefined'
            CHECK (
                candidate_fit_reason_code IN (
                    'undefined',
                    'ok',
                    'lang',
                    'loc',
                    'tech',
                    'role_mismatch',
                    'skill_mismatch'
                )
            )
            """
        )
    if "candidate_fit_reason" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN candidate_fit_reason TEXT NOT NULL DEFAULT ''"
        )
    if "status" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN status TEXT NOT NULL DEFAULT 'New'"
        )
    ensure_job_statuses(connection)
    connection.execute("UPDATE jobs SET status = 'Closed' WHERE status = 'Close'")
    connection.execute("UPDATE jobs SET status = 'Applied' WHERE status = 'Approved'")
    connection.execute("DELETE FROM job_statuses WHERE code = 'Approved'")
    invalid_statuses = [
        row[0]
        for row in connection.execute(
            """
            SELECT DISTINCT j.status
            FROM jobs j
            LEFT JOIN job_statuses s ON s.code = j.status
            WHERE s.code IS NULL
            """
        ).fetchall()
    ]
    if invalid_statuses:
        raise ValueError(
            "Unsupported job statuses in database: "
            + ", ".join(repr(status) for status in invalid_statuses)
        )
    invalid_reason_codes = [
        row[0]
        for row in connection.execute(
            """
            SELECT DISTINCT candidate_fit_reason_code
            FROM jobs
            WHERE candidate_fit_reason_code NOT IN (
                'undefined',
                'ok',
                'lang',
                'loc',
                'tech',
                'role_mismatch',
                'skill_mismatch'
            )
            """
        ).fetchall()
    ]
    if invalid_reason_codes:
        raise ValueError(
            "Unsupported candidate-fit reason codes in database: "
            + ", ".join(repr(code) for code in invalid_reason_codes)
        )
    if (
        not jobs_status_fk_present(connection)
        or not jobs_candidate_fit_reason_code_check_present(connection)
    ):
        rebuild_jobs_with_current_schema(connection, schema_sql)


def ensure_job_statuses(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS job_statuses (
            code TEXT PRIMARY KEY,
            sort_order INTEGER NOT NULL DEFAULT 0
        )
        """
    )
    connection.executemany(
        """
        INSERT OR IGNORE INTO job_statuses (code, sort_order)
        VALUES (?, ?)
        """,
        [(status, (index + 1) * 10) for index, status in enumerate(DEFAULT_JOB_STATUSES)],
    )


def jobs_status_fk_present(connection: sqlite3.Connection) -> bool:
    row = connection.execute(
        """
        SELECT sql
        FROM sqlite_master
        WHERE type = 'table' AND name = 'jobs'
        """
    ).fetchone()
    ddl = row[0] if row else ""
    return "status TEXT NOT NULL DEFAULT 'New' REFERENCES job_statuses(code)" in ddl


def jobs_candidate_fit_reason_code_check_present(
    connection: sqlite3.Connection,
) -> bool:
    row = connection.execute(
        """
        SELECT sql
        FROM sqlite_master
        WHERE type = 'table' AND name = 'jobs'
        """
    ).fetchone()
    ddl = row[0] if row else ""
    return (
        "candidate_fit_reason_code TEXT NOT NULL DEFAULT 'undefined' CHECK" in ddl
        and "'role_mismatch'" in ddl
        and "'skill_mismatch'" in ddl
    )


def jobs_new_table_sql(schema_sql: str) -> str:
    marker = "CREATE TABLE IF NOT EXISTS jobs ("
    start = schema_sql.index(marker)
    end_marker = "\n);\n\nCREATE TABLE IF NOT EXISTS job_languages"
    end = schema_sql.index(end_marker, start) + len("\n);")
    return schema_sql[start:end].replace(
        "CREATE TABLE IF NOT EXISTS jobs",
        "CREATE TABLE jobs_new",
        1,
    )


def table_columns(connection: sqlite3.Connection, table: str) -> list[str]:
    return [row[1] for row in connection.execute(f"PRAGMA table_info({table})")]


def rebuild_jobs_with_current_schema(
    connection: sqlite3.Connection,
    schema_sql: str,
) -> None:
    connection.commit()
    foreign_keys_enabled = int(
        connection.execute("PRAGMA foreign_keys").fetchone()[0]
    )
    connection.execute("PRAGMA foreign_keys = OFF")
    try:
        connection.execute("DROP TABLE IF EXISTS jobs_new")
        connection.execute(jobs_new_table_sql(schema_sql))
        columns = [
            column
            for column in table_columns(connection, "jobs_new")
            if column in set(table_columns(connection, "jobs"))
        ]
        column_sql = ", ".join(columns)
        connection.execute(
            f"INSERT INTO jobs_new ({column_sql}) SELECT {column_sql} FROM jobs"
        )
        connection.execute("DROP TABLE jobs")
        connection.execute("ALTER TABLE jobs_new RENAME TO jobs")
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.execute(f"PRAGMA foreign_keys = {foreign_keys_enabled}")


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
    source = required_text(record, "source")
    source_url = required_text(record, "source_url")
    required_text(record, "title")
    collected = connection.execute(
        """
        SELECT source_job.id
        FROM source_jobs source_job
        JOIN source_job_texts text ON text.source_job_ref = source_job.id
        WHERE source_job.source = ? AND source_job.source_job_id = ?
        """,
        (source, source_job_id(source, source_url)),
    ).fetchone()
    if collected is None:
        raise ValueError(
            "Collected job data is missing for "
            f"{source}:{source_url}; save collector output before analysis"
        )
    source_job_ref = mark_url_status(connection, source, source_url, "SAVED")
    salary = clean(record["salary"])
    if salary.lower() == "unknown":
        raise ValueError("salary must be empty when unavailable, not 'unknown'")

    connection.execute(
        """
        INSERT INTO jobs (
            source_job_ref,
            status,
            location,
            remote_type,
            remote_scope,
            relocation,
            job_interest,
            candidate_fit_percent,
            candidate_fit_reason_code,
            candidate_fit_reason,
            seniority,
            role,
            salary,
            summary,
            notes,
            added_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(source_job_ref) DO UPDATE SET
            location = excluded.location,
            remote_type = excluded.remote_type,
            remote_scope = excluded.remote_scope,
            relocation = excluded.relocation,
            job_interest = excluded.job_interest,
            candidate_fit_percent = excluded.candidate_fit_percent,
            candidate_fit_reason_code = excluded.candidate_fit_reason_code,
            candidate_fit_reason = excluded.candidate_fit_reason,
            seniority = excluded.seniority,
            role = excluded.role,
            salary = excluded.salary,
            summary = excluded.summary,
            notes = excluded.notes,
            updated_at = CURRENT_TIMESTAMP
        """,
        (
            source_job_ref,
            job_status(record.get("status")),
            clean(record["location"]),
            required_text(record, "remote_type"),
            clean(record["remote_scope"]),
            required_text(record, "relocation"),
            required_int(record, "job_interest"),
            required_int(record, "candidate_fit_percent"),
            candidate_fit_reason_code(record.get("candidate_fit_reason_code")),
            clean(record.get("candidate_fit_reason")),
            required_text(record, "seniority"),
            required_text(record, "role"),
            salary,
            clean(record["summary"]),
            clean(record["notes"]),
            clean(record["added_at"]),
        ),
    )
    job_id = int(
        connection.execute(
            "SELECT id FROM jobs WHERE source_job_ref = ?",
            (source_job_ref,),
        ).fetchone()[0]
    )

    replace_languages(connection, job_id, record["languages"])
    replace_technologies(connection, job_id, record["technologies"])
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
        name = required_text(language, "name")

        level = normalize_level(language["level"])
        rank = required_int(language, "level_rank")
        raw_value = clean(language["raw_value"])
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
            (job_id, language_id, level, rank, raw_value),
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
        name = required_text(technology, "name")

        req_type = requirement_type(technology["requirement"])
        level = normalize_level(technology["level"])
        technology_id = get_or_create_id(connection, "technologies", name)
        raw_value = clean(technology["raw_value"])

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
                required_int(technology, "level_rank"),
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

    where = "j.id = ?"
    value: Any = job_id
    if source_url:
        where = "text.source_url = ?"
        value = source_url

    row = connection.execute(
        f"""
        SELECT
            j.id,
            sj.source,
            {", ".join(
                "coalesce(c.name, '') AS company"
                if column == "company"
                else f"text.{column}"
                if column in {"source_url", "title"}
                else f"j.{column}"
                for column in JOB_COLUMNS[2:]
            )}
        FROM jobs j
        JOIN source_jobs sj ON sj.id = j.source_job_ref
        LEFT JOIN source_job_texts text ON text.source_job_ref = j.source_job_ref
        LEFT JOIN companies c ON c.id = text.company_id
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
        "status": job_status(job["status"]),
        "source_url": clean(job["source_url"]),
        "added_at": clean(job["added_at"]),
        "salary": clean(job["salary"]),
        "relocation": clean(job["relocation"], "NO"),
        "job_interest": int(job["job_interest"] or 0),
        "candidate_fit_percent": int(job["candidate_fit_percent"] or 0),
        "candidate_fit_reason_code": candidate_fit_reason_code(
            job["candidate_fit_reason_code"]
        ),
        "candidate_fit_reason": clean(job["candidate_fit_reason"]),
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
                WHEN 'core' THEN 1
                WHEN 'required' THEN 2
                WHEN 'important' THEN 3
                WHEN 'desired' THEN 4
                WHEN 'nice_to_have' THEN 5
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
