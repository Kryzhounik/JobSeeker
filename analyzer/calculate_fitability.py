from __future__ import annotations

import argparse
import configparser
import sqlite3
from pathlib import Path


LANGUAGE_RANKS = {
    "a1": 1,
    "a2": 2,
    "b1": 3,
    "b2": 4,
    "c1": 5,
    "c2": 6,
    "native": 6,
    "fluent": 6,
}


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def load_resume(path: Path) -> dict[str, int]:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path, encoding="utf-8")

    result: dict[str, int] = {}
    if not config.has_section("languages"):
        return result

    for language, level in config.items("languages"):
        result[language.lower()] = language_rank(level)
    return result


def language_rank(level: object) -> int:
    text = str(level or "").strip().lower()
    for token, rank in LANGUAGE_RANKS.items():
        if token in text:
            return rank
    return 0


def apply_schema(connection: sqlite3.Connection, schema_path: Path) -> None:
    ensure_fitability_column(connection)
    connection.executescript(schema_path.read_text(encoding="utf-8"))


def ensure_fitability_column(connection: sqlite3.Connection) -> None:
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
    if "fitability_percent" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN fitability_percent INTEGER NOT NULL DEFAULT 100"
        )


def language_fitability(
    required_languages: list[sqlite3.Row],
    resume_languages: dict[str, int],
) -> tuple[int, str]:
    english_limit = LANGUAGE_RANKS["b2"]

    for row in required_languages:
        language = str(row["language"] or "").strip()
        required_rank = int(row["level_rank"] or language_rank(row["level"]))
        key = language.lower()

        if key == "english" and required_rank > english_limit:
            return 0, f"English above B2 required: {row['level']}"

        own_rank = resume_languages.get(key)
        if own_rank is None:
            return 0, f"Required language not in resume: {language}"

        if required_rank and own_rank < required_rank:
            return 0, f"{language} required {row['level']}, resume lower"

    return 100, "language filter passed"


def calculate_fitability(
    db_path: Path,
    schema_path: Path,
    resume_path: Path,
) -> list[tuple[int, int, str, str]]:
    resume_languages = load_resume(resume_path)

    with sqlite3.connect(db_path) as connection:
        connection.row_factory = sqlite3.Row
        apply_schema(connection, schema_path)
        jobs = connection.execute("SELECT id, title FROM jobs ORDER BY id").fetchall()
        updates: list[tuple[int, int, str, str]] = []

        for job in jobs:
            languages = connection.execute(
                """
                SELECT l.name AS language, jl.level, jl.level_rank
                FROM job_languages jl
                JOIN languages l ON l.id = jl.language_id
                WHERE jl.job_id = ?
                ORDER BY jl.level_rank DESC, l.name COLLATE NOCASE
                """,
                (job["id"],),
            ).fetchall()
            score, reason = language_fitability(languages, resume_languages)
            connection.execute(
                "UPDATE jobs SET fitability_percent = ? WHERE id = ?",
                (score, job["id"]),
            )
            updates.append((job["id"], score, job["title"], reason))

        connection.commit()
        return updates


def main() -> None:
    root = project_root()
    parser = argparse.ArgumentParser(description="Calculate job fitability filters.")
    parser.add_argument("--db", default=str(root / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(root / "analyzer" / "db" / "schema.sql"))
    parser.add_argument("--resume", default=str(root / "analyzer" / "config" / "resume.ini"))
    args = parser.parse_args()

    updates = calculate_fitability(
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        resume_path=Path(args.resume),
    )
    for job_id, score, title, reason in updates:
        print(f"{job_id}: {score}% {title} ({reason})")


if __name__ == "__main__":
    main()
