from __future__ import annotations

import argparse
import re
import sqlite3
from pathlib import Path

from extract_linkedin_readable_text_v1 import ROOT
from extract_linkedin_readable_text_v1 import DATA_ROOT
from extract_linkedin_readable_text_v1 import TextExtractor
from extract_linkedin_readable_text_v1 import input_paths
from extract_linkedin_readable_text_v1 import source_url
from db.job_registry import source_job_id
from db.migrate import migrate_database
from db.readable_text import has_readable_text
from db.readable_text import save_readable_text


TAIL_MARKERS = (
    "Set alert for similar jobs",
    "More jobs",
    "Looking for talent?",
    "Show more",
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
GUEST_SIGN_IN_START = "Join or sign in to find your next job"
GUEST_DESCRIPTION_START = "Description"

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


class LinkedInTextExtractor(TextExtractor):
    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        classes = dict(attrs).get("class", "") or ""
        if "show-more-less-html__markup" in classes.split():
            self.parts.append("\nDescription\n")
        super().handle_starttag(tag, attrs)


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
    skip_guest_sign_in = False

    for raw_line in text.splitlines():
        line = repair_mojibake(raw_line)
        line = re.sub(r"\s+", " ", line).strip()
        if not line:
            continue

        if line in TAIL_MARKERS:
            break

        if line == GUEST_SIGN_IN_START:
            skip_guest_sign_in = True
            continue

        if skip_guest_sign_in:
            if line == GUEST_DESCRIPTION_START:
                skip_guest_sign_in = False
            else:
                continue

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
    parser = LinkedInTextExtractor()
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
    force: bool,
    limit: int,
    db_path: Path,
) -> None:
    paths = input_paths(input_path)
    if limit > 0:
        paths = paths[:limit]

    migrate_database(db_path)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        for raw_path in paths:
            url = source_url(source, raw_path)
            job_id = source_job_id(source, url) if url else raw_path.stem
            if has_readable_text(connection, source, job_id) and not force:
                print(f"skip existing readable text {source}:{job_id}")
            else:
                html_chars = raw_path.stat().st_size
                text = readable_text(source, raw_path)
                save_readable_text(connection, source, job_id, text)
                text_chars = len(text)
                approx_tokens = max(1, text_chars // 4)
                print(
                    f"saved readable text {source}:{job_id} "
                    f"html_chars={html_chars} text_chars={text_chars} "
                    f"approx_tokens={approx_tokens}"
                )
            connection.commit()


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Extract readable text v2 from raw LinkedIn job HTML."
    )
    parser.add_argument("--source", required=True)
    parser.add_argument("--input")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    input_path = (
        Path(args.input)
        if args.input
        else DATA_ROOT / "raw" / args.source / "pages"
    )
    convert(
        args.source,
        input_path,
        args.force,
        args.limit,
        Path(args.db),
    )


if __name__ == "__main__":
    main()
