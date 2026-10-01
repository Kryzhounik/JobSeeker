"""Database requests shared by the Tkinter and JavaFX clients.

Public functions accept a database path and simple values. Structured results
are JSON strings so JavaFX can consume the same API through jpy without
exposing SQLite connections or Row objects across the language boundary.
"""

from __future__ import annotations

from contextlib import contextmanager
import json
import sqlite3
import sys
from collections.abc import Iterator
from pathlib import Path
from typing import Any


PROJECT_ROOT = Path(__file__).resolve().parents[1]
DRIVER_ROOT = PROJECT_ROOT / "Driver"
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from db.applications import (  # noqa: E402
    create_for_source_urls,
    list_application_statuses,
    list_applications,
    update_status as update_application_status,
)
from db.companies import (  # noqa: E402
    create_company as db_create_company,
    update_company_linkedin_id,
    update_company_priority,
)
from db.job_mapper import apply_refilter_transitions  # noqa: E402
from db.migrate import migrate_database  # noqa: E402


DEFAULT_STATUS_VALUES = ("New", "Checked", "Postponed", "Applied", "Closed")


class DataConflictError(ValueError):
    """A requested value conflicts with an existing unique database value."""


@contextmanager
def _connection(
    db_path: str | Path,
    *,
    readonly: bool,
) -> Iterator[sqlite3.Connection]:
    database = Path(db_path).expanduser().resolve()
    if not database.is_file():
        raise FileNotFoundError(f"Database not found: {database}")

    migrate_database(db_path=database)
    if readonly:
        connection = sqlite3.connect(
            f"{database.as_uri()}?mode=ro",
            uri=True,
        )
    else:
        connection = sqlite3.connect(database)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys = ON")
    if readonly:
        connection.execute("PRAGMA query_only = ON")

    try:
        yield connection
        if not readonly:
            connection.commit()
    except Exception:
        if not readonly:
            connection.rollback()
        raise
    finally:
        connection.close()


def _json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


def _rows_as_dicts(rows: Any) -> list[dict[str, Any]]:
    return [dict(row) for row in rows]


def load_status_values(db_path: str | Path) -> str:
    if not Path(db_path).is_file():
        return _json(DEFAULT_STATUS_VALUES)
    try:
        with _connection(db_path, readonly=True) as connection:
            rows = connection.execute(
                """
                SELECT code
                FROM job_statuses
                ORDER BY sort_order, code COLLATE NOCASE
                """
            ).fetchall()
    except sqlite3.Error:
        return _json(DEFAULT_STATUS_VALUES)

    values = [str(row[0]).strip() for row in rows if str(row[0]).strip()]
    if not values:
        return _json(DEFAULT_STATUS_VALUES)
    for status in DEFAULT_STATUS_VALUES:
        if status not in values:
            values.append(status)
    return _json(values)


def load_reason_code_descriptions(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        rows = load_reason_code_descriptions_from_connection(connection)
    return _json(rows)


def load_reason_code_descriptions_from_connection(
    connection: sqlite3.Connection,
) -> dict[str, str]:
    rows = connection.execute(
        """
        SELECT code, description
        FROM candidate_fit_reason_codes
        ORDER BY code COLLATE NOCASE
        """
    ).fetchall()
    return {
        str(row[0]).strip(): str(row[1] or "").strip()
        for row in rows
        if row[0] is not None and str(row[0]).strip()
    }


def load_jobs(db_path: str | Path, filters_json: str) -> str:
    filters = json.loads(filters_json or "{}")
    statuses = [str(value) for value in filters.get("statuses", []) if value]
    reasons = [str(value).strip() for value in filters.get("reasons", []) if str(value).strip()]
    where_parts: list[str] = []
    parameters: list[Any] = []

    if statuses:
        placeholders = ", ".join("?" for _ in statuses)
        where_parts.append(f"jl.status IN ({placeholders})")
        parameters.extend(statuses)
    else:
        where_parts.append("0")

    if not bool(filters.get("show_zero", False)):
        where_parts.append("CAST(jl.score AS INTEGER) <> 0")

    id_query = str(filters.get("id_query", "")).strip()
    if id_query:
        where_parts.append(
            """
            (
                CAST(j.id AS TEXT) = ?
                OR sj.source_job_id = ?
                OR text.source_url LIKE ?
            )
            """
        )
        parameters.extend((id_query, id_query, f"%{id_query}%"))

    added_from = str(filters.get("added_from", "")).strip()
    if added_from:
        where_parts.append("date(jl.added_at) >= date(?)")
        parameters.append(added_from)

    if reasons:
        placeholders = ", ".join("?" for _ in reasons)
        where_parts.append(
            f"coalesce(j.candidate_fit_reason_code, '') IN ({placeholders})"
        )
        parameters.extend(reasons)

    where_sql = "WHERE " + " AND ".join(where_parts)
    with _connection(db_path, readonly=True) as connection:
        rows = connection.execute(
            f"""
            SELECT
                jl.score,
                jl.fit,
                jl.interest,
                jl.status,
                jl.remote_scope,
                jl.relocation,
                jl.location,
                jl.company,
                jl.title,
                jl.role,
                jl.seniority,
                jl.primary_language,
                jl.salary,
                jl.added_at,
                jl.source_url,
                text.company_id,
                a.id AS application_id,
                coalesce(company_apps.application_count, 0)
                    AS company_application_count,
                coalesce(j.candidate_fit_reason_code, '')
                    AS candidate_fit_reason_code,
                coalesce(j.candidate_fit_reason, '')
                    AS candidate_fit_reason
            FROM job_list jl
            JOIN jobs j ON j.id = jl.job_id
            JOIN source_jobs sj ON sj.id = j.source_job_ref
            LEFT JOIN source_job_texts text
                ON text.source_job_ref = j.source_job_ref
            LEFT JOIN applications a ON a.job_id = j.id
            LEFT JOIN (
                SELECT
                    company_text.company_id,
                    count(company_applications.id) AS application_count
                FROM jobs company_jobs
                JOIN source_job_texts company_text
                    ON company_text.source_job_ref = company_jobs.source_job_ref
                JOIN applications company_applications
                    ON company_applications.job_id = company_jobs.id
                WHERE company_text.company_id IS NOT NULL
                GROUP BY company_text.company_id
            ) company_apps ON company_apps.company_id = text.company_id
            {where_sql}
            """,
            parameters,
        ).fetchall()
    return _json(_rows_as_dicts(rows))


def load_job_detail(db_path: str | Path, source_url: str) -> str:
    with _connection(db_path, readonly=True) as connection:
        row = connection.execute(
            """
            SELECT
                j.id,
                sj.source_job_id,
                text.title,
                coalesce(c.name, '') AS company,
                j.location,
                j.remote_type,
                j.remote_scope,
                j.status,
                j.relocation,
                j.seniority,
                j.role,
                j.salary,
                j.job_interest AS interest,
                j.candidate_fit_percent AS fit,
                CAST((
                    j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent
                    + 9999
                ) / 10000 AS INTEGER) AS score,
                text.source_url,
                j.summary,
                coalesce(text.readable_text, '') AS readable_text,
                j.added_at
            FROM jobs j
            JOIN source_jobs sj ON sj.id = j.source_job_ref
            JOIN source_job_texts text
                ON text.source_job_ref = j.source_job_ref
            LEFT JOIN companies c ON c.id = text.company_id
            WHERE text.source_url = ?
            """,
            (source_url,),
        ).fetchone()
        if row is None:
            raise KeyError(f"Job not found: {source_url}")

        job_id = int(row["id"])
        languages = [
            str(language_row["language"] or "")
            for language_row in connection.execute(
                """
                SELECT
                    l.name
                    || coalesce(': ' || nullif(jl.level, ''), '') AS language
                FROM job_languages jl
                JOIN languages l ON l.id = jl.language_id
                JOIN jobs j ON j.id = jl.job_id
                WHERE jl.job_id = ?
                ORDER BY
                    CASE WHEN jl.language_id = j.primary_language_id THEN 0 ELSE 1 END,
                    jl.level_rank DESC,
                    l.name COLLATE NOCASE
                """,
                (job_id,),
            ).fetchall()
        ]
        technologies = _rows_as_dicts(
            connection.execute(
                """
                SELECT
                    t.name AS technology,
                    CASE jt.requirement_type
                        WHEN 'core' THEN 'core'
                        WHEN 'required' THEN 'req'
                        WHEN 'important' THEN 'imp'
                        WHEN 'desired' THEN 'des'
                        WHEN 'nice_to_have' THEN 'opt'
                        ELSE jt.requirement_type
                    END AS req,
                    CASE
                        WHEN jt.requirement_type = 'nice_to_have' THEN 'nice to have'
                        WHEN lower(coalesce(jt.level, '')) IN (
                            '', 'listed', 'mentioned', 'required', 'required/listed'
                        ) THEN
                            CASE jt.level_rank
                                WHEN 1 THEN 'nice to have'
                                WHEN 2 THEN 'junior'
                                WHEN 3 THEN 'regular'
                                WHEN 4 THEN 'advanced'
                                WHEN 5 THEN 'master'
                                ELSE ''
                            END
                        WHEN lower(coalesce(jt.level, '')) LIKE '%experience required%'
                            THEN 'regular'
                        ELSE jt.level
                    END AS level,
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
        )

    detail = dict(row)
    detail["languages"] = "; ".join(language for language in languages if language)
    return _json({
        "detail": detail,
        "languages": languages,
        "technologies": technologies,
    })


def load_collected_jobs(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        rows = load_collected_jobs_from_connection(connection)
    return _json(rows)


def load_collected_jobs_from_connection(
    connection: sqlite3.Connection,
) -> list[dict[str, Any]]:
    rows = connection.execute(
        """
        SELECT
            sj.id AS source_job_ref,
            sj.source,
            sj.source_job_id,
            sj.processing_status,
            sj.collection_method,
            text.source_url,
            text.title,
            coalesce(company.name, '') AS company,
            text.collected_location,
            text.collected_workplace,
            text.collected_salary,
            text.readable_text
        FROM source_job_texts text
        JOIN source_jobs sj ON sj.id = text.source_job_ref
        LEFT JOIN companies company ON company.id = text.company_id
        WHERE length(trim(text.source_url)) > 0
            OR length(trim(text.title)) > 0
        ORDER BY sj.id DESC
        """
    ).fetchall()
    return _rows_as_dicts(rows)


def load_config_rows(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        rows = connection.execute(
            """
            SELECT "key", config_name, value
            FROM config
            ORDER BY "key"
            """
        ).fetchall()
    return _json(_rows_as_dicts(rows))


def set_config_value(db_path: str | Path, config_key: int, value: bool) -> None:
    with _connection(db_path, readonly=False) as connection:
        result = connection.execute(
            "UPDATE config SET value = ? WHERE \"key\" = ?",
            ("1" if value else "0", int(config_key)),
        )
        if result.rowcount != 1:
            raise KeyError(f"Config not found: {config_key}")


def load_companies(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        rows = connection.execute(
            """
            SELECT
                c.id,
                c.priority,
                c.name,
                c.linkedin_id,
                c.blacklisted,
                count(a.id) AS application_count
            FROM companies c
            LEFT JOIN source_job_texts text ON text.company_id = c.id
            LEFT JOIN jobs j ON j.source_job_ref = text.source_job_ref
            LEFT JOIN applications a ON a.job_id = j.id
            GROUP BY
                c.id,
                c.priority,
                c.name,
                c.linkedin_id,
                c.blacklisted
            """
        ).fetchall()
    return _json(_rows_as_dicts(rows))


def create_company(db_path: str | Path, name: str, linkedin_id: str = "") -> int:
    try:
        with _connection(db_path, readonly=False) as connection:
            return db_create_company(connection, name, linkedin_id)
    except sqlite3.IntegrityError as error:
        error_text = str(error).casefold()
        if "companies.name" in error_text:
            raise DataConflictError(f"Company already exists: {name}") from error
        if "companies.linkedin_id" in error_text:
            raise DataConflictError(
                f"LinkedIn ID already exists: {linkedin_id}"
            ) from error
        raise


def set_company_linkedin_id(
    db_path: str | Path,
    company_id: int,
    linkedin_id: str,
) -> None:
    try:
        with _connection(db_path, readonly=False) as connection:
            update_company_linkedin_id(connection, int(company_id), linkedin_id)
    except sqlite3.IntegrityError as error:
        error_text = str(error).casefold()
        if "companies.linkedin_id" in error_text:
            raise DataConflictError(
                f"LinkedIn ID already exists: {linkedin_id}"
            ) from error
        raise


def set_company_priority(
    db_path: str | Path,
    company_id: int,
    priority: bool,
) -> None:
    with _connection(db_path, readonly=False) as connection:
        update_company_priority(connection, int(company_id), priority)


def set_company_blacklisted(
    db_path: str | Path,
    company_id: int,
    blacklisted: bool,
) -> None:
    with _connection(db_path, readonly=False) as connection:
        result = connection.execute(
            "UPDATE companies SET blacklisted = ? WHERE id = ?",
            (int(bool(blacklisted)), int(company_id)),
        )
        if result.rowcount != 1:
            raise KeyError(f"Company not found: {company_id}")


def load_applications(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        rows = list_applications(connection)
    return _json(_rows_as_dicts(rows))


def load_application_status_values(db_path: str | Path) -> str:
    with _connection(db_path, readonly=True) as connection:
        statuses = list_application_statuses(connection)
    return _json(statuses)


def set_application_status(
    db_path: str | Path,
    application_id: int,
    status: str,
) -> None:
    with _connection(db_path, readonly=False) as connection:
        update_application_status(connection, int(application_id), status)


def find_job_by_source_url(db_path: str | Path, source_url: str) -> str:
    with _connection(db_path, readonly=True) as connection:
        row = connection.execute(
            """
            SELECT job.id, job.status
            FROM jobs job
            JOIN source_job_texts text
                ON text.source_job_ref = job.source_job_ref
            WHERE text.source_url = ?
            """,
            (source_url,),
        ).fetchone()
    return _json(dict(row) if row is not None else None)


def set_job_status(
    db_path: str | Path,
    source_urls_json: str,
    status: str,
    applied_at: str,
) -> str:
    source_urls = list(dict.fromkeys(
        url for url in json.loads(source_urls_json or "[]") if url
    ))
    if not source_urls:
        return _json({
            "updated_count": 0,
            "created_applications": 0,
            "application_ids": {},
            "company_application_counts": {},
        })

    created_applications = 0
    application_ids: dict[str, str] = {}
    company_application_counts: dict[str, str] = {}
    with _connection(db_path, readonly=False) as connection:
        placeholders = ", ".join("?" for _ in source_urls)
        result = connection.execute(
            f"""
            UPDATE jobs
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE source_job_ref IN (
                SELECT source_job_ref
                FROM source_job_texts
                WHERE source_url IN ({placeholders})
            )
            """,
            [status, *source_urls],
        )
        if result.rowcount == 0:
            raise KeyError("Selected jobs were not found.")

        if status == "Applied":
            created_applications = create_for_source_urls(
                connection,
                source_urls,
                applied_at,
            )
            application_ids = {
                str(row["source_url"]): str(row["application_id"])
                for row in connection.execute(
                    f"""
                    SELECT text.source_url, a.id AS application_id
                    FROM jobs j
                    JOIN source_job_texts text
                        ON text.source_job_ref = j.source_job_ref
                    JOIN applications a ON a.job_id = j.id
                    WHERE text.source_url IN ({placeholders})
                    """,
                    source_urls,
                )
            }
            company_application_counts = {
                str(row["company_id"]): str(row["application_count"])
                for row in connection.execute(
                    f"""
                    SELECT
                        company_text.company_id,
                        count(company_applications.id) AS application_count
                    FROM jobs company_jobs
                    JOIN source_job_texts company_text
                        ON company_text.source_job_ref = company_jobs.source_job_ref
                    JOIN applications company_applications
                        ON company_applications.job_id = company_jobs.id
                    WHERE company_text.company_id IN (
                        SELECT company_id
                        FROM source_job_texts
                        WHERE source_url IN ({placeholders})
                            AND company_id IS NOT NULL
                    )
                    GROUP BY company_text.company_id
                    """,
                    source_urls,
                )
            }

    return _json({
        "updated_count": int(result.rowcount),
        "created_applications": created_applications,
        "application_ids": application_ids,
        "company_application_counts": company_application_counts,
    })


def save_scores(
    db_path: str | Path,
    source_url: str,
    fit: int,
    interest: int,
) -> int:
    with _connection(db_path, readonly=False) as connection:
        result = connection.execute(
            """
            UPDATE jobs
            SET candidate_fit_percent = ?,
                job_interest = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE source_job_ref = (
                SELECT source_job_ref
                FROM source_job_texts
                WHERE source_url = ?
            )
            """,
            (int(fit), int(interest), source_url),
        )
        if result.rowcount != 1:
            raise KeyError(f"Job not found: {source_url}")
        score_row = connection.execute(
            "SELECT score FROM job_list WHERE source_url = ?",
            (source_url,),
        ).fetchone()
        if score_row is None:
            raise KeyError(f"Job not found in job_list: {source_url}")
        score = int(score_row["score"])
    return score


def load_linkedin_availability_candidates(db_path: str | Path) -> str:
    score_sql = """
        CAST((
            j.job_interest * j.candidate_fit_percent * j.candidate_fit_percent
            + 9999
        ) / 10000 AS INTEGER)
    """
    with _connection(db_path, readonly=True) as connection:
        rows = connection.execute(
            f"""
            SELECT
                text.source_url,
                text.title,
                coalesce(c.name, '') AS company,
                {score_sql} AS score
            FROM jobs j
            JOIN source_job_texts text
                ON text.source_job_ref = j.source_job_ref
            LEFT JOIN companies c ON c.id = text.company_id
            WHERE j.status = ?
                AND {score_sql} > 0
                AND text.source_url LIKE ?
            ORDER BY score DESC, j.added_at DESC, j.id DESC
            """,
            ("New", "%linkedin.com/%"),
        ).fetchall()
    return _json(_rows_as_dicts(rows))


def mark_jobs_closed(
    db_path: str | Path,
    source_urls_json: str,
    closed_status: str,
) -> int:
    source_urls = list(dict.fromkeys(
        url for url in json.loads(source_urls_json or "[]") if url
    ))
    if not source_urls:
        return 0

    with _connection(db_path, readonly=False) as connection:
        placeholders = ", ".join("?" for _ in source_urls)
        result = connection.execute(
            f"""
            UPDATE jobs
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE status = 'New'
                AND source_job_ref IN (
                    SELECT source_job_ref
                    FROM source_job_texts
                    WHERE source_url IN ({placeholders})
                )
            """,
            [closed_status, *source_urls],
        )
    return int(result.rowcount)


def apply_refilter_candidates(db_path: str | Path, candidates_json: str) -> int:
    candidates = json.loads(candidates_json or "[]")
    with _connection(db_path, readonly=False) as connection:
        applied_refs = apply_refilter_transitions(
            connection,
            [
                (
                    int(candidate["source_job_ref"]),
                    str(candidate["previous_status"]).strip(),
                    str(candidate["action"]).strip(),
                )
                for candidate in candidates
            ],
        )
    return len(applied_refs)
