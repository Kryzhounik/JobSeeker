from __future__ import annotations

import argparse
import csv
import sqlite3
from pathlib import Path


EMPTY_VALUES = {"", "unknown", "n/a", "none", "-"}


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


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    connection.executescript(schema_path.read_text(encoding="utf-8"))


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


def replace_requirements(
    connection: sqlite3.Connection,
    job_id: int,
    row: dict[str, str],
) -> None:
    connection.execute("DELETE FROM job_languages WHERE job_id = ?", (job_id,))
    connection.execute("DELETE FROM job_technologies WHERE job_id = ?", (job_id,))

    for item in split_items(row.get("language_requirements")):
        name, level = parse_item(item)
        connection.execute(
            """
            INSERT INTO job_languages (job_id, language, level, raw_value)
            VALUES (?, ?, ?, ?)
            """,
            (job_id, name, level, item),
        )

    for item in split_items(row.get("technology_requirements")):
        name, level = parse_item(item)
        connection.execute(
            """
            INSERT INTO job_technologies (
                job_id,
                technology,
                level,
                is_required,
                raw_value
            )
            VALUES (?, ?, ?, 1, ?)
            """,
            (job_id, name, level, item),
        )

    for item in split_items(row.get("nice_to_have_technologies")):
        name, level = parse_item(item)
        connection.execute(
            """
            INSERT INTO job_technologies (
                job_id,
                technology,
                level,
                is_required,
                raw_value
            )
            VALUES (?, ?, ?, 0, ?)
            """,
            (job_id, name, level, item),
        )


def import_jobs(csv_path: Path, db_path: Path, schema_path: Path, limit: int) -> int:
    db_path.parent.mkdir(parents=True, exist_ok=True)
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
    args = parser.parse_args()

    count = import_jobs(
        csv_path=Path(args.csv),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        limit=args.limit,
    )
    print(f"Imported {count} job(s) into {args.db}")


if __name__ == "__main__":
    main()
