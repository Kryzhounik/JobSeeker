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
from common.job_record import COUNTRY_NAMES
from common.job_record import clean
from common.job_record import infer_role
from common.job_record import label_value
from common.job_record import language_record
from common.job_record import location_join
from common.job_record import normalize_seniority
from common.job_record import remote_scope_from_country
from common.job_record import salary_from_employment_types
from common.job_record import skill_level
from common.job_record import technology_record
from common.job_record import today_iso


SITE_URL = "https://justjoin.it"


def page_slug(path: Path) -> str:
    return path.stem


def source_url_for_slug(slug: str) -> str:
    return f"{SITE_URL}/job-offer/{slug}"


def extract_offer(path: Path) -> dict[str, Any]:
    slug = page_slug(path)
    flight = extract_flight_payload(path.read_text(encoding="utf-8", errors="replace"))
    marker = f'"slug":"{slug}"'
    marker_index = flight.find(marker)
    if marker_index < 0:
        raise ValueError(f"Could not find offer payload for slug={slug}")

    start = flight.rfind("{", 0, marker_index)
    if start < 0:
        raise ValueError(f"Could not find offer object start for slug={slug}")

    raw_object = extract_json_object(flight, start)
    offer = json.loads(raw_object)
    if offer.get("slug") != slug:
        raise ValueError(f"Parsed wrong offer payload: {offer.get('slug')} != {slug}")
    return offer


def extract_flight_payload(html: str) -> str:
    parts: list[str] = []
    for match in re.finditer(r"<script>self\.__next_f\.push\((.*?)\)</script>", html, re.S):
        try:
            payload = json.loads(match.group(1))
        except json.JSONDecodeError:
            continue
        if len(payload) > 1 and isinstance(payload[1], str):
            parts.append(payload[1])
    return "".join(parts)


def extract_json_object(text: str, start: int) -> str:
    depth = 0
    in_string = False
    escaped = False

    for index in range(start, len(text)):
        char = text[index]
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue

        if char == '"':
            in_string = True
        elif char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return text[start : index + 1]

    raise ValueError("JSON object is not closed")


def offer_to_record(offer: dict[str, Any]) -> dict[str, Any]:
    slug = str(offer.get("slug") or "")
    title = clean(offer.get("title"))
    company = clean(offer.get("companyName"))
    remote_type = label_value(offer.get("workplaceType"), "unknown")
    country = COUNTRY_NAMES.get(clean(offer.get("countryCode")).upper(), "")

    return {
        "source": "justjoin",
        "source_url": source_url_for_slug(slug),
        "title": title,
        "company": company,
        "location": location_join(
            clean(offer.get("city")),
            clean(offer.get("street")),
            country,
        ),
        "remote_type": remote_type,
        "remote_scope": remote_scope_from_country(remote_type, country),
        "relocation": "NO",
        "valuation": 0,
        "seniority": normalize_seniority(
            label_value(offer.get("experienceLevel"), "unknown")
        ),
        "role": infer_role(title),
        "salary": salary_from_employment_types(offer.get("employmentTypes") or []),
        "summary": f"{title} at {company}".strip(),
        "pros": "",
        "cons": "",
        "notes": "Imported mechanically from JustJoinIT raw HTML.",
        "added_at": today_iso(),
        "languages": languages(offer.get("languages") or []),
        "technologies": technologies(offer),
    }


def languages(raw_languages: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for language in raw_languages:
        code = clean(language.get("code")).lower()
        level = clean(language.get("level"))
        if code:
            result.append(language_record(code, level))
    return result


def technologies(offer: dict[str, Any]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for skill in offer.get("requiredSkills") or []:
        result.append(technology(skill, "required"))
    for skill in offer.get("niceToHaveSkills") or []:
        result.append(technology(skill, "nice_to_have"))
    return result


def technology(skill: dict[str, Any], requirement: str) -> dict[str, str]:
    name = clean(skill.get("name") or skill.get("id"))
    level = skill_level(skill.get("level"))
    return technology_record(name, requirement, level)


def import_raw_pages(
    raw_dir: Path,
    db_path: Path,
    schema_path: Path,
    limit: int,
    force: bool,
) -> list[tuple[str, str]]:
    pages = sorted((raw_dir / "pages").glob("*.html"))
    if limit > 0:
        pages = pages[:limit]

    results: list[tuple[str, str]] = []
    with sqlite3.connect(db_path) as connection:
        apply_schema(connection, schema_path)
        done = analyzed_urls(connection)

        for path in pages:
            url = source_url_for_slug(page_slug(path))
            if url in done and not force:
                results.append(("skip", url))
                continue

            try:
                offer = extract_offer(path)
                record = offer_to_record(offer)
                write_job(connection, record)
                done.add(record["source_url"])
                results.append(("import", record["source_url"]))
            except Exception as error:
                results.append(("error", f"{url} :: {error}"))

        connection.commit()

    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Import JustJoinIT raw HTML into SQLite.")
    parser.add_argument("--raw-dir", default=str(ROOT / "data" / "raw" / "justjoin"))
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
