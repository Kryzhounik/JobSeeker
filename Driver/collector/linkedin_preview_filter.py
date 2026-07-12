from __future__ import annotations

import argparse
import configparser
import json
import re
import sqlite3
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
COMMON_ROOT = ROOT / "common"
if str(COMMON_ROOT) not in sys.path:
    sys.path.insert(0, str(COMMON_ROOT))
LOGGING_ROOT = ROOT / "collector" / "logging"
if str(LOGGING_ROOT) not in sys.path:
    sys.path.insert(0, str(LOGGING_ROOT))

from linkedin_logger import record_preview_filter


DEFAULT_CONFIG = ROOT / "collector" / "config" / "linkedin_preview_filter.ini"
DEFAULT_DB = ROOT.parent / "Data" / "jobs.sqlite"


def load_config(path: Path = DEFAULT_CONFIG) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.read(path, encoding="utf-8")
    return config


def csv_values(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def enabled(config: configparser.ConfigParser) -> bool:
    return config.get("filters", "enabled", fallback="on").strip().lower() not in {
        "off",
        "false",
        "no",
        "0",
    }


def blocked_terms(config: configparser.ConfigParser) -> list[str]:
    terms = csv_values(config.get("title", "blocked_terms", fallback=""))
    terms_file = config.get("title", "blocked_terms_file", fallback="").strip()
    if terms_file:
        path = Path(terms_file)
        if not path.is_absolute():
            path = DEFAULT_CONFIG.parent / path
        if path.exists():
            for line in path.read_text(encoding="utf-8").splitlines():
                term = line.strip()
                if term and not term.startswith("#"):
                    terms.append(term)
    return terms


def matches_term(text: str, term: str) -> bool:
    haystack = text.lower()
    needle = term.lower().strip()
    if not needle:
        return False

    if any(not char.isalnum() for char in needle):
        return needle in haystack

    return re.search(rf"(?<![a-z0-9]){re.escape(needle)}(?![a-z0-9])", haystack) is not None


def source_job_id(preview: dict[str, Any]) -> str:
    value = str(preview.get("job_id") or "").strip()
    if value:
        return value
    source_url = str(preview.get("source_url") or "").strip()
    match = re.search(r"/jobs/view/(\d+)", source_url)
    return match.group(1) if match else ""


def ensure_dedup_schema(connection: sqlite3.Connection) -> None:
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
    if "source_job_id" not in columns:
        connection.execute(
            "ALTER TABLE jobs ADD COLUMN source_job_id TEXT NOT NULL DEFAULT ''"
        )
    connection.execute(
        "CREATE INDEX IF NOT EXISTS idx_jobs_source_job_id ON jobs(source, source_job_id)"
    )
    connection.execute(
        """
        UPDATE jobs
        SET source_job_id = substr(
            source_url,
            instr(source_url, '/jobs/view/') + length('/jobs/view/'),
            instr(
                substr(
                    source_url,
                    instr(source_url, '/jobs/view/') + length('/jobs/view/')
                ),
                '/'
            ) - 1
        )
        WHERE source = 'linkedin'
            AND source_job_id = ''
            AND instr(source_url, '/jobs/view/') > 0
        """
    )


def is_duplicate_preview(preview: dict[str, Any], db_path: Path = DEFAULT_DB) -> bool:
    if not db_path.exists():
        return False

    job_id = source_job_id(preview)
    if not job_id:
        return False

    with sqlite3.connect(db_path) as connection:
        ensure_dedup_schema(connection)
        row = connection.execute(
            """
            SELECT 1
            FROM jobs
            WHERE source = 'linkedin'
                AND source_job_id = ?
            LIMIT 1
            """,
            (job_id,),
        ).fetchone()
        return row is not None
    return False


def decide_preview(
    preview: dict[str, Any],
    config_path: Path = DEFAULT_CONFIG,
) -> dict[str, Any]:
    config = load_config(config_path)
    title = str(preview.get("title") or "").strip()

    if not enabled(config):
        return {
            "preview_decision": "open",
            "preview_reason": "preview filter disabled",
            "preview_blocked_terms": [],
        }

    matches = [term for term in blocked_terms(config) if matches_term(title, term)]
    if matches:
        return {
            "preview_decision": "skip",
            "preview_reason": "title blocked: " + ", ".join(matches),
            "preview_blocked_terms": matches,
        }

    if is_duplicate_preview(preview):
        return {
            "preview_decision": "skip",
            "preview_reason": "duplicate source_job_id",
            "preview_blocked_terms": [],
        }

    when_unsure = config.get("decision", "when_unsure", fallback="open").strip().lower()
    return {
        "preview_decision": "open" if when_unsure != "skip" else "skip",
        "preview_reason": "no preview skip signals",
        "preview_blocked_terms": [],
    }


def apply_decision(payload: Any, config_path: Path) -> Any:
    if isinstance(payload, list):
        return [apply_decision(item, config_path) for item in payload]

    if not isinstance(payload, dict):
        raise ValueError("Preview payload must be a JSON object or list.")

    preview = payload.get("preview")
    if not isinstance(preview, dict):
        preview = payload

    result = decide_preview(preview, config_path)
    return record_preview_filter({**payload, **result})


def load_payload(input_path: str, title: str) -> Any:
    if title:
        return {"title": title}

    if input_path == "-":
        return json.load(sys.stdin)

    return json.loads(Path(input_path).read_text(encoding="utf-8"))


def write_payload(payload: Any, output_path: str) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    if output_path == "-":
        print(text, end="")
        return
    Path(output_path).write_text(text, encoding="utf-8")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Apply cheap preview filter to job cards.")
    parser.add_argument("--input", "-i", default="-", help="Preview JSON file, list, or stdin.")
    parser.add_argument("--output", "-o", default="-", help="Output JSON file or stdout.")
    parser.add_argument("--title", default="", help="Quick check for a single title.")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = parser.parse_args()

    payload = load_payload(args.input, args.title)
    result = apply_decision(payload, Path(args.config))
    write_payload(result, args.output)


if __name__ == "__main__":
    main()
