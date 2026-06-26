from __future__ import annotations

import argparse
import csv
import sqlite3
from pathlib import Path


EMPTY_VALUES = {"", "unknown", "n/a", "none", "-"}
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
TECH_MENTION_RANK_2 = (
    "listed",
    "mentioned",
    "required",
)
TECH_OPTIONAL_RANK_1 = (
    "nice to have",
    "nice-to-have",
    "will be a plus",
    "would be a plus",
    "plus",
)


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def is_empty(value: str | None) -> bool:
    return (value or "").strip().lower() in EMPTY_VALUES


def split_items(value: str | None) -> list[str]:
    if is_empty(value):
        return []

    text = (value or "").strip()
    separator = ";" if ";" in text else ","
    return [item.strip() for item in text.split(separator) if item.strip()]


def parse_item(item: str) -> tuple[str, str | None]:
    if ":" not in item:
        return item.strip(), None

    name, level = item.split(":", 1)
    return name.strip(), level.strip() or None


def level_rank(level: str | None) -> int | None:
    if not level:
        return 1

    normalized = level.lower().replace("_", "-").replace("/", " ")
    for token, rank in LEVEL_RANKS.items():
        if token in normalized:
            return rank
    return 1


def technology_level_rank(level: str | None, requirement_type: str) -> int:
    if requirement_type == "nice_to_have":
        return 1

    if not level:
        return 2

    normalized = level.lower().replace("_", "-").replace("/", " ")
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


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    connection.executescript(schema_path.read_text(encoding="utf-8"))


def get_or_create_id(
    connection: sqlite3.Connection,
    table: str,
    name: str,
) -> int:
    if table not in {"languages", "technologies"}:
        raise ValueError(f"Unsupported dictionary table: {table}")

    connection.execute(
        f"INSERT OR IGNORE INTO {table} (name) VALUES (?)",
        (name,),
    )
    row = connection.execute(
        f"SELECT id FROM {table} WHERE name = ?",
        (name,),
    ).fetchone()
    return int(row[0])


def insert_job(connection: sqlite3.Connection, row: dict[str, str]) -> int:
    cursor = connection.execute(
        """
        INSERT INTO jobs (
            source,
            source_url,
            title,
            company,
            location,
            remote_type,
            seniority,
            role,
            salary,
            status,
            summary,
            pros,
            cons,
            notes,
            added_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(source_url) DO UPDATE SET
            title = excluded.title,
            company = excluded.company,
            location = excluded.location,
            remote_type = excluded.remote_type,
            seniority = excluded.seniority,
            role = excluded.role,
            salary = excluded.salary,
            status = excluded.status,
            summary = excluded.summary,
            pros = excluded.pros,
            cons = excluded.cons,
            notes = excluded.notes,
            updated_at = CURRENT_TIMESTAMP
        """,
        (
            "justjoin",
            row["source_url"],
            row["title"],
            row.get("company"),
            row.get("location"),
            row.get("remote_type"),
            row.get("seniority"),
            row.get("role"),
            row.get("salary"),
            row.get("status") or "new",
            row.get("summary"),
            row.get("pros"),
            row.get("cons"),
            row.get("notes"),
            row.get("added_at"),
        ),
    )
    if cursor.lastrowid:
        return cursor.lastrowid

    existing = connection.execute(
        "SELECT id FROM jobs WHERE source_url = ?",
        (row["source_url"],),
    ).fetchone()
    return int(existing[0])


def replace_languages(
    connection: sqlite3.Connection,
    job_id: int,
    row: dict[str, str],
) -> None:
    connection.execute("DELETE FROM job_languages WHERE job_id = ?", (job_id,))

    primary_language_id = None
    primary_rank = -1
    for item in split_items(row.get("language_requirements")):
        name, level = parse_item(item)
        rank = level_rank(level)
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
            (job_id, language_id, level, rank, item),
        )

        if rank is not None and rank > primary_rank:
            primary_language_id = language_id
            primary_rank = rank

    connection.execute(
        "UPDATE jobs SET primary_language_id = ? WHERE id = ?",
        (primary_language_id, job_id),
    )


def replace_technologies(
    connection: sqlite3.Connection,
    job_id: int,
    row: dict[str, str],
) -> None:
    connection.execute("DELETE FROM job_technologies WHERE job_id = ?", (job_id,))

    for requirement_type, column in (
        ("required", "technology_requirements"),
        ("nice_to_have", "nice_to_have_technologies"),
    ):
        for item in split_items(row.get(column)):
            name, level = parse_item(item)
            technology_id = get_or_create_id(connection, "technologies", name)
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
                    requirement_type,
                    level,
                    technology_level_rank(level, requirement_type),
                    item,
                ),
            )


def replace_requirements(
    connection: sqlite3.Connection,
    job_id: int,
    row: dict[str, str],
) -> None:
    replace_languages(connection, job_id, row)
    replace_technologies(connection, job_id, row)


def import_jobs(
    csv_path: Path,
    db_path: Path,
    schema_path: Path,
    limit: int,
    recreate: bool,
) -> int:
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if recreate and db_path.exists():
        db_path.unlink()

    with sqlite3.connect(db_path) as connection:
        apply_schema(connection, schema_path)
        with csv_path.open("r", encoding="utf-8", newline="") as file:
            reader = csv.DictReader(file)
            count = 0
            for row in reader:
                if limit and count >= limit:
                    break
                job_id = insert_job(connection, row)
                replace_requirements(connection, job_id, row)
                count += 1
        return count


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default=str(root / "data" / "jobs.csv"))
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "db" / "schema.sql"))
    parser.add_argument("--limit", type=int, default=2)
    parser.add_argument("--recreate", action="store_true")
    args = parser.parse_args()

    count = import_jobs(
        csv_path=Path(args.csv),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        limit=args.limit,
        recreate=args.recreate,
    )
    print(f"Imported {count} job(s) into {args.db}")


if __name__ == "__main__":
    main()
