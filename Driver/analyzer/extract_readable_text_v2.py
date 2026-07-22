from __future__ import annotations

import argparse
import re
import sqlite3
from pathlib import Path

from extract_readable_text_v1 import ROOT
from extract_readable_text_v1 import DATA_ROOT
from extract_readable_text_v1 import TextExtractor
from extract_readable_text_v1 import input_paths
from extract_readable_text_v1 import output_path
from extract_readable_text_v1 import source_url
from db.job_registry import mark_status
from db.job_registry import source_job_id
from db.migrate import migrate_database


TAIL_MARKERS = (
    "Set alert for similar jobs",
    "More jobs",
    "Looking for talent?",
)

CHROME_LINES = {
    "0 notifications",
    "Skip to main contentSkip to primary contentSkip to asideSkip to footer",
    "Home",
    "My Network",
    "Jobs",
    "Messaging",
    "Me",
    "For Business",
    "Try Premium for $0",
    "Apply",
    "Save",
    "Use AI to assess how you fit",
    "Show match details",
    "Tailor my resume",
    "Help me stand out",
    "Show all",
    "Job poster",
    "Message",
}
CHROME_PREFIXES = (
    "Get AI-powered advice on this job",
    "Easy Apply is now LinkedIn Apply.",
)
BLOCK_UNTIL_ABOUT_JOB = {
    "People you can reach out to",
    "Meet the hiring team",
}

MOJIBAKE_MARKERS = (
    "В·",
    "вЂ",
    "в‚",
    "в†",
    "вњ",
    "вљ",
    "рџ",
    "Рџ",
)


def mojibake_score(text: str) -> int:
    return sum(text.count(marker) for marker in MOJIBAKE_MARKERS)


def repair_mojibake(line: str) -> str:
    """Repair UTF-8 text accidentally decoded as a legacy Windows encoding."""
    original_score = mojibake_score(line)
    if original_score == 0:
        return line

    best = line
    best_score = original_score
    for encoding in ("cp1251", "cp1252", "latin-1"):
        try:
            candidate = line.encode(encoding).decode("utf-8")
        except (UnicodeEncodeError, UnicodeDecodeError):
            continue
        candidate_score = mojibake_score(candidate)
        if candidate_score < best_score:
            best = candidate
            best_score = candidate_score

    return best


def should_drop_line(line: str) -> bool:
    if line in CHROME_LINES:
        return True
    if re.fullmatch(r"\d+Notifications", line):
        return True
    return any(line.startswith(prefix) for prefix in CHROME_PREFIXES)


def normalize_text(text: str) -> str:
    text = text.replace("\xa0", " ")
    lines: list[str] = []
    previous = ""
    skip_until_about_job = False

    for raw_line in text.splitlines():
        line = repair_mojibake(raw_line)
        line = re.sub(r"\s+", " ", line).strip()
        if not line:
            continue

        if line in TAIL_MARKERS:
            break

        if skip_until_about_job:
            if line == "About the job":
                skip_until_about_job = False
            else:
                continue

        if line in BLOCK_UNTIL_ABOUT_JOB:
            skip_until_about_job = True
            continue

        if should_drop_line(line):
            continue

        if line == previous:
            continue
        lines.append(line)
        previous = line

    return "\n".join(lines).strip() + "\n"


def readable_text(source: str, raw_path: Path) -> str:
    parser = TextExtractor()
    parser.feed(raw_path.read_text(encoding="utf-8", errors="replace"))
    text = normalize_text(parser.text())
    metadata = [
        f"source: {source}",
        f"raw_file: {raw_path.as_posix()}",
    ]
    url = source_url(source, raw_path)
    if url:
        metadata.append(f"source_url: {url}")
    return "\n".join(metadata) + "\n\n" + text


def convert(
    source: str,
    input_path: Path,
    output_dir: Path,
    force: bool,
    limit: int,
    db_path: Path,
) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    paths = input_paths(input_path)
    if limit > 0:
        paths = paths[:limit]

    migrate_database(db_path)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        for raw_path in paths:
            out_path = output_path(raw_path, output_dir)
            if out_path.exists() and not force:
                print(f"skip existing {out_path}")
            else:
                html_chars = raw_path.stat().st_size
                text = readable_text(source, raw_path)
                out_path.write_text(text, encoding="utf-8")
                text_chars = len(text)
                approx_tokens = max(1, text_chars // 4)
                print(
                    f"saved {out_path} "
                    f"html_chars={html_chars} text_chars={text_chars} "
                    f"approx_tokens={approx_tokens}"
                )

            url = source_url(source, raw_path)
            job_id = source_job_id(source, url) if url else raw_path.stem
            mark_status(connection, source, job_id, "CLEANED")
            connection.commit()


def main() -> None:
    parser = argparse.ArgumentParser(description="Extract readable text v2 from raw job HTML.")
    parser.add_argument("--source", required=True)
    parser.add_argument("--input")
    parser.add_argument("--out-dir")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    input_path = (
        Path(args.input)
        if args.input
        else DATA_ROOT / "raw" / args.source / "pages"
    )
    output_dir = (
        Path(args.out_dir)
        if args.out_dir
        else DATA_ROOT / "readable_v2" / args.source / "pages"
    )
    convert(
        args.source,
        input_path,
        output_dir,
        args.force,
        args.limit,
        Path(args.db),
    )


if __name__ == "__main__":
    main()
