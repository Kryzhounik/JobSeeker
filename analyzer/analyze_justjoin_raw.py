from __future__ import annotations

import argparse
from datetime import date
import json
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
if str(Path(__file__).resolve().parent) not in sys.path:
    sys.path.insert(0, str(Path(__file__).resolve().parent))

from write_job import apply_schema, write_job


SITE_URL = "https://justjoin.it"

LANGUAGE_NAMES = {
    "en": "English",
    "pl": "Polish",
    "ru": "Russian",
    "uk": "Ukrainian",
    "ua": "Ukrainian",
    "de": "German",
    "fr": "French",
    "es": "Spanish",
    "it": "Italian",
}

COUNTRY_NAMES = {
    "PL": "Poland",
    "UA": "Ukraine",
    "US": "United States",
    "DE": "Germany",
    "GB": "United Kingdom",
    "CZ": "Czechia",
    "LT": "Lithuania",
    "LV": "Latvia",
    "EE": "Estonia",
    "MD": "Moldova",
    "GE": "Georgia",
    "RS": "Serbia",
}

SKILL_LEVELS = {
    1: "nice to have",
    2: "junior",
    3: "regular",
    4: "advanced",
    5: "master",
}


def page_slug(path: Path) -> str:
    return path.stem


def source_url_for_slug(slug: str) -> str:
    return f"{SITE_URL}/job-offer/{slug}"


def analyzed_urls(connection: sqlite3.Connection) -> set[str]:
    ensure_jobs_table(connection)
    return {
        row[0]
        for row in connection.execute("SELECT source_url FROM jobs").fetchall()
    }


def ensure_jobs_table(connection: sqlite3.Connection) -> None:
    connection.execute(
        """
        CREATE TABLE IF NOT EXISTS jobs (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            source_url TEXT NOT NULL UNIQUE,
            title TEXT NOT NULL
        )
        """
    )


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
        "location": location(offer, country),
        "remote_type": remote_type,
        "remote_scope": remote_scope(remote_type, country),
        "relocation": "NO",
        "valuation": 0,
        "seniority": seniority(label_value(offer.get("experienceLevel"), "unknown")),
        "role": role(title),
        "salary": salary(offer.get("employmentTypes") or []),
        "summary": f"{title} at {company}".strip(),
        "pros": "",
        "cons": "",
        "notes": "Imported mechanically from JustJoinIT raw HTML.",
        "added_at": date.today().isoformat(),
        "languages": languages(offer.get("languages") or []),
        "technologies": technologies(offer),
    }


def label_value(value: Any, default: str = "") -> str:
    if isinstance(value, dict):
        return clean(value.get("value") or value.get("label"), default)
    return clean(value, default)


def clean(value: Any, default: str = "") -> str:
    text = str(value or "").strip()
    return text if text else default


def location(offer: dict[str, Any], country: str) -> str:
    parts = [
        clean(offer.get("city")),
        clean(offer.get("street")),
        country,
    ]
    return ", ".join(part for part in parts if part and part != "-")


def remote_scope(remote_type: str, country: str) -> str:
    if remote_type != "remote":
        return ""
    return country or "unknown"


def seniority(value: str) -> str:
    normalized = value.lower()
    if normalized in {"mid", "middle", "regular"}:
        return "middle"
    if normalized in {"senior", "lead", "junior", "intern"}:
        return normalized
    return value or "unknown"


def role(title: str) -> str:
    normalized = title.lower()
    if "fullstack" in normalized or "full-stack" in normalized:
        return "fullstack"
    if "test" in normalized or "qa" in normalized or "sdet" in normalized:
        return "qa"
    if "devops" in normalized or "platform" in normalized:
        return "devops"
    if "frontend" in normalized or "front-end" in normalized:
        return "frontend"
    return "backend"


def salary(employment_types: list[dict[str, Any]]) -> str:
    originals = [
        item for item in employment_types
        if item.get("currencySource") == "original"
    ]
    item = originals[0] if originals else (employment_types[0] if employment_types else None)
    if not item:
        return "unknown"

    currency = clean(item.get("currency"))
    unit = clean(item.get("unit"))
    contract = clean(item.get("type"))
    gross = item.get("gross")
    tax = "gross" if gross else "net" if gross is False else ""
    start = item.get("from")
    end = item.get("to")

    if start is None and end is None:
        amount = "unknown"
    elif start == end or end is None:
        amount = format_number(start)
    elif start is None:
        amount = format_number(end)
    else:
        amount = f"{format_number(start)}-{format_number(end)}"

    details = " ".join(part for part in [currency, tax, per_unit(unit), contract] if part)
    return " ".join(part for part in [amount, details] if part).strip() or "unknown"


def format_number(value: Any) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def per_unit(unit: str) -> str:
    if not unit:
        return ""
    return f"/{unit}"


def languages(raw_languages: list[dict[str, Any]]) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for language in raw_languages:
        code = clean(language.get("code")).lower()
        name = LANGUAGE_NAMES.get(code, code.upper())
        level = clean(language.get("level"))
        if name:
            result.append({"name": name, "level": level})
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
    level_number = skill.get("level")
    level = SKILL_LEVELS.get(level_number, clean(level_number, "listed"))
    if requirement == "nice_to_have":
        level = "nice to have"
    return {
        "name": name,
        "requirement": requirement,
        "level": level,
        "raw_value": f"{name}: {level}",
    }


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
