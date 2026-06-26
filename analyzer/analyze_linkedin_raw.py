from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

from write_job import apply_schema, write_job
from common.db_import import analyzed_urls
from common.job_record import clean
from common.job_record import infer_role
from common.job_record import location_join
from common.job_record import normalize_remote_type
from common.job_record import normalize_seniority
from common.job_record import today_iso
from common.text_job_analysis import country_from_location
from common.text_job_analysis import languages_from_text
from common.text_job_analysis import relocation_from_text
from common.text_job_analysis import remote_scope_from_text
from common.text_job_analysis import salary_from_raw_text
from common.text_job_analysis import technologies_from_text


def load_raw(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def canonical_linkedin_url(url: str) -> str:
    value = clean(url)
    match = re.search(r"(?:currentJobId=|/jobs/view/(?:[^/?#]+-)?)(\d{7,})", value)
    if match:
        return f"https://www.linkedin.com/jobs/view/{match.group(1)}/"
    return value


def metadata_from_text(text: str) -> dict[str, str]:
    lines = [clean(line) for line in text.splitlines() if clean(line)]
    result = {"company": "", "location": "", "workplace_type": ""}
    for line in lines[:80]:
        if " • " not in line:
            continue
        left, right = line.split(" • ", 1)
        if not result["company"]:
            result["company"] = clean(left)
        workplace = re.search(r"\(([^)]+)\)", right)
        if workplace and not result["workplace_type"]:
            result["workplace_type"] = clean(workplace.group(1))
        if not result["location"]:
            result["location"] = clean(re.sub(r"\s*\([^)]+\)", "", right))
        break
    return result


def title_parts(raw_title: str) -> tuple[str, str, str]:
    match = re.match(r"(.+?) hiring (.+?) in (.+)$", clean(raw_title), flags=re.I)
    if match:
        return clean(match.group(2)), clean(match.group(1)), clean(match.group(3))
    return clean(raw_title), "", ""


def title_and_company(raw_title: str, raw_company: str) -> tuple[str, str]:
    title = clean(re.sub(r"\s*\([^)]*вакансия[^)]*\)\s*", "", raw_title))
    company = clean(raw_company)
    if " | " in title:
        left, right = [clean(part) for part in title.split(" | ", 1)]
        title = left or title
        company = company or right
    return title, company


def description_section(text: str) -> str:
    start_markers = (
        "About this job",
        "About the job",
        "Об этой вакансии",
    )
    end_markers = (
        "Отправлять оповещения",
        "About the company",
        "О компании",
        "You may be interested",
        "Similar jobs",
    )

    start = first_marker_index(text, start_markers)
    if start >= 0:
        text = text[start:]

    end = first_marker_index(text, end_markers)
    if end > 0:
        text = text[:end]

    return text


def first_marker_index(text: str, markers: tuple[str, ...]) -> int:
    normalized = text.lower()
    positions = [
        normalized.find(marker.lower())
        for marker in markers
        if normalized.find(marker.lower()) >= 0
    ]
    return min(positions) if positions else -1


def linkedin_record(raw: dict[str, Any]) -> dict[str, Any]:
    text = clean(raw.get("text") or raw.get("description"))
    description = description_section(text)
    source_url = canonical_linkedin_url(clean(raw.get("source_url") or raw.get("url")))
    text_metadata = metadata_from_text(text)
    raw_title, raw_company, raw_location = title_parts(clean(raw.get("title"), "unknown"))
    title, company = title_and_company(
        raw_title,
        clean(raw.get("company")) or raw_company or text_metadata["company"],
    )
    location = clean(raw.get("location")) or raw_location or text_metadata["location"]
    workplace_type = clean(raw.get("workplace_type")) or text_metadata["workplace_type"]
    remote_type = normalize_remote_type(workplace_type, f"{title}\n{description[:2000]}")
    country = country_from_location(location)

    return {
        "source": "linkedin",
        "source_url": source_url,
        "title": title,
        "company": company,
        "location": location_join(
            location,
            country if country and country.lower() not in location.lower() else "",
        ),
        "remote_type": remote_type,
        "remote_scope": remote_scope_from_text(remote_type, location, description),
        "relocation": relocation_from_text(description, location),
        "valuation": 0,
        "seniority": normalize_seniority(f"{title} {description[:800]}"),
        "role": infer_role(title, description[:1200]),
        "salary": salary_from_raw_text(clean(raw.get("salary")), description),
        "summary": f"{title} at {company}".strip(),
        "pros": "",
        "cons": "",
        "notes": "Imported mechanically from LinkedIn raw browser text.",
        "added_at": today_iso(),
        "languages": languages_from_text(description),
        "technologies": technologies_from_text(f"{title}\n{description}"),
    }


def import_raw_pages(
    raw_dir: Path,
    db_path: Path,
    schema_path: Path,
    limit: int,
    force: bool,
) -> list[tuple[str, str]]:
    pages = sorted((raw_dir / "pages").glob("*.json"))
    if limit > 0:
        pages = pages[:limit]

    results: list[tuple[str, str]] = []
    with sqlite3.connect(db_path) as connection:
        apply_schema(connection, schema_path)
        done = analyzed_urls(connection)

        for path in pages:
            try:
                raw = load_raw(path)
                record = linkedin_record(raw)
                if record["source_url"] in done and not force:
                    results.append(("skip", record["source_url"]))
                    continue

                write_job(connection, record)
                done.add(record["source_url"])
                results.append(("import", record["source_url"]))
            except Exception as error:
                results.append(("error", f"{path.name} :: {error}"))

        connection.commit()

    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Import LinkedIn raw JSON into SQLite.")
    parser.add_argument("--raw-dir", default=str(ROOT / "data" / "raw" / "linkedin"))
    parser.add_argument("--db", default=str(ROOT / "data" / "jobs.sqlite"))
    parser.add_argument("--schema", default=str(ROOT / "analyzer" / "db" / "schema.sql"))
    parser.add_argument("--limit", type=int, default=0)
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    results = import_raw_pages(
        raw_dir=Path(args.raw_dir),
        db_path=Path(args.db),
        schema_path=Path(args.schema),
        limit=args.limit,
        force=args.force,
    )
    for status, value in results:
        print(f"{status}: {value}")
    print(f"processed {len(results)} raw pages")


if __name__ == "__main__":
    main()
