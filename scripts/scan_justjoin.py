#!/usr/bin/env python3
"""Small JustJoinIT scanner for the local CSV prototype.

This intentionally stays boring: HTTP GET public pages, parse visible/structured
data, wait between vacancy requests, and avoid browser impersonation.
"""

from __future__ import annotations

import argparse
import csv
import datetime as dt
import html
import json
import re
import sys
import time
from pathlib import Path
from typing import Iterable
from urllib.parse import urljoin
from urllib.request import Request, urlopen


BASE_URL = "https://justjoin.it"
DEFAULT_USER_AGENT = (
    "JobSeekerBot/0.1 "
    "(+https://github.com/Kryzhounik/JobSeeker; purpose=personal-job-research)"
)

CSV_FIELDS = [
    "added_at",
    "source_url",
    "title",
    "company",
    "location",
    "remote_type",
    "seniority",
    "role",
    "language_requirements",
    "technology_requirements",
    "nice_to_have_technologies",
    "stack",
    "salary",
    "match_score",
    "status",
    "summary",
    "pros",
    "cons",
    "notes",
]

HUMAN_LANGUAGES = {
    "english",
    "polish",
    "german",
    "french",
    "spanish",
    "italian",
    "russian",
    "ukrainian",
    "belarusian",
    "czech",
    "slovak",
    "lithuanian",
    "latvian",
    "estonian",
    "dutch",
    "portuguese",
    "romanian",
    "hungarian",
}


def fetch_text(url: str, user_agent: str) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": user_agent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.8,ru;q=0.6",
        },
    )
    with urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def clean_text(value: str) -> str:
    value = re.sub(r"<[^>]+>", " ", value)
    value = html.unescape(value)
    value = value.replace("\xa0", " ")
    return re.sub(r"\s+", " ", value).strip()


def find_meta_content(page_html: str, property_name: str) -> str:
    pattern = (
        r'<meta\s+(?:property|name)=["\']'
        + re.escape(property_name)
        + r'["\']\s+content=["\'](.*?)["\']'
    )
    match = re.search(pattern, page_html, re.IGNORECASE | re.DOTALL)
    return clean_text(match.group(1)) if match else ""


def extract_job_urls(search_html: str, search_url: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()

    for match in re.finditer(r"/job-offer/[^\"'<>\\\s]+", search_html):
        url = html.unescape(match.group(0)).split("?")[0]
        full_url = urljoin(search_url, url)
        if full_url not in seen:
            seen.add(full_url)
            urls.append(full_url)

    return urls


def extract_json_ld(page_html: str) -> dict:
    scripts = re.findall(
        r'<script\s+type=["\']application/ld\+json["\']>(.*?)</script>',
        page_html,
        re.IGNORECASE | re.DOTALL,
    )
    for script in scripts:
        try:
            data = json.loads(script)
        except json.JSONDecodeError:
            continue
        if isinstance(data, dict) and data.get("@type") == "JobPosting":
            return data
    return {}


def extract_tech_pairs(page_html: str) -> list[tuple[str, str]]:
    marker = re.search(r"<h3[^>]*>\s*Tech stack\s*</h3>", page_html, re.IGNORECASE)
    if not marker:
        return []

    section = page_html[marker.end() :]
    next_heading = re.search(r"<h3[^>]*>", section, re.IGNORECASE)
    if next_heading:
        section = section[: next_heading.start()]

    pairs: list[tuple[str, str]] = []
    pattern = r"<h4[^>]*>(.*?)</h4>.*?<span[^>]*>(.*?)</span>"
    for name, level in re.findall(pattern, section, re.IGNORECASE | re.DOTALL):
        clean_name = clean_text(name)
        clean_level = clean_text(level)
        if clean_name and clean_level:
            pairs.append((clean_name, clean_level))
    return pairs


def format_pairs(pairs: Iterable[tuple[str, str]]) -> str:
    values = [f"{name}: {level}" for name, level in pairs]
    return "; ".join(values) if values else "unknown"


def split_language_and_tech(
    pairs: Iterable[tuple[str, str]],
) -> tuple[list[tuple[str, str]], list[tuple[str, str]]]:
    language_pairs: list[tuple[str, str]] = []
    tech_pairs: list[tuple[str, str]] = []

    for name, level in pairs:
        if name.lower() in HUMAN_LANGUAGES:
            language_pairs.append((name, level))
        else:
            tech_pairs.append((name, level))

    return language_pairs, tech_pairs


def infer_seniority(title: str) -> str:
    text = title.lower()
    if "intern" in text or "trainee" in text:
        return "intern"
    if "junior" in text:
        return "junior"
    if "middle" in text or "mid" in text or "regular" in text:
        return "middle"
    if "lead" in text:
        return "lead"
    if "senior" in text or "principal" in text or "staff" in text:
        return "senior"
    return "unknown"


def infer_role(title: str, stack: str) -> str:
    text = f"{title} {stack}".lower()
    if any(word in text for word in ("devops", "sre", "infrastructure")):
        return "devops"
    if any(word in text for word in ("qa", "tester", "test automation")):
        return "qa"
    if any(word in text for word in ("data engineer", "data scientist", "analytics")):
        return "data"
    if any(word in text for word in ("machine learning", "ml ", "ai engineer")):
        return "ml_ai"
    if any(word in text for word in ("frontend", "front-end", "react", "vue", "angular")):
        if any(word in text for word in ("backend", "back-end", "java", "spring", "node")):
            return "fullstack"
        return "frontend"
    if any(word in text for word in ("backend", "back-end", "java", "spring", "python", "go ")):
        return "backend"
    return "other"


def format_location(data: dict) -> str:
    address = data.get("jobLocation", {}).get("address", {})
    if not isinstance(address, dict):
        return "unknown"

    parts = [
        address.get("addressLocality"),
        address.get("addressRegion"),
        address.get("addressCountry"),
    ]
    value = ", ".join(str(part) for part in parts if part)
    return value or "unknown"


def format_remote_type(data: dict, page_html: str) -> str:
    location_type = str(data.get("jobLocationType", "")).upper()
    if location_type == "TELECOMMUTE":
        return "remote"

    visible_text = clean_text(page_html).lower()
    if "fully remote" in visible_text or "100% remote" in visible_text:
        return "remote"
    if "hybrid" in visible_text:
        return "hybrid"
    if "office" in visible_text:
        return "office"
    return "unknown"


def format_salary(data: dict, page_html: str) -> str:
    og_description = find_meta_content(page_html, "og:description")
    if "|" in og_description:
        salary_part = og_description.split("|", 1)[0].strip()
        if re.search(r"\d", salary_part):
            return salary_part

    salary = data.get("baseSalary")
    if isinstance(salary, dict):
        currency = salary.get("currency", "")
        value = salary.get("value", {})
        if isinstance(value, dict):
            min_value = value.get("minValue")
            max_value = value.get("maxValue")
            unit = value.get("unitText", "")
            if min_value and max_value:
                return f"{min_value}-{max_value} {currency}/{unit}".strip("/")

    return "unknown"


def extract_nice_to_have(description: str) -> str:
    match = re.search(
        r"Nice to have(.*?)(?:What makes this role interesting|Conditions|About the company|$)",
        description,
        re.IGNORECASE | re.DOTALL,
    )
    if not match:
        return "unknown"

    value = clean_text(match.group(1))
    value = re.sub(r"\bExperience\b", "; Experience", value)
    value = re.sub(r"\bAWS\b", "; AWS", value)
    value = re.sub(r"\bCI/CD\b", "; CI/CD", value)
    return value.strip(" ;") or "unknown"


def first_sentence(description: str) -> str:
    value = clean_text(description)
    if not value:
        return "unknown"

    match = re.search(r"(.{40,220}?[.!?])\s", value + " ")
    if match:
        return match.group(1)
    return value[:220].rstrip()


def parse_job_page(url: str, page_html: str) -> dict[str, str]:
    data = extract_json_ld(page_html)
    tech_pairs = extract_tech_pairs(page_html)
    language_pairs, technology_pairs = split_language_and_tech(tech_pairs)

    title = data.get("title") or find_meta_content(page_html, "og:title") or "unknown"
    company_data = data.get("hiringOrganization", {})
    company = company_data.get("name") if isinstance(company_data, dict) else ""
    description = data.get("description", "")
    stack_values = [name for name, _ in technology_pairs]
    stack = ", ".join(stack_values) if stack_values else "unknown"

    return {
        "added_at": dt.date.today().isoformat(),
        "source_url": url,
        "title": clean_text(str(title)),
        "company": clean_text(str(company or "unknown")),
        "location": format_location(data),
        "remote_type": format_remote_type(data, page_html),
        "seniority": infer_seniority(str(title)),
        "role": infer_role(str(title), stack),
        "language_requirements": format_pairs(language_pairs),
        "technology_requirements": format_pairs(technology_pairs),
        "nice_to_have_technologies": extract_nice_to_have(str(description)),
        "stack": stack,
        "salary": format_salary(data, page_html),
        "match_score": "unknown",
        "status": "new",
        "summary": first_sentence(str(description)),
        "pros": "unknown",
        "cons": "unknown",
        "notes": "Parsed from JustJoinIT public HTML/JSON-LD; subjective scoring not applied.",
    }


def existing_urls(csv_path: Path) -> set[str]:
    if not csv_path.exists():
        return set()

    with csv_path.open("r", encoding="utf-8-sig", newline="") as handle:
        reader = csv.DictReader(handle)
        return {row.get("source_url", "") for row in reader if row.get("source_url")}


def append_rows(csv_path: Path, rows: list[dict[str, str]]) -> None:
    csv_path.parent.mkdir(parents=True, exist_ok=True)
    needs_header = not csv_path.exists() or csv_path.stat().st_size == 0

    with csv_path.open("a", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=CSV_FIELDS)
        if needs_header:
            writer.writeheader()
        for row in rows:
            writer.writerow({field: row.get(field, "unknown") for field in CSV_FIELDS})


def print_rows(rows: list[dict[str, str]]) -> None:
    for index, row in enumerate(rows, start=1):
        print(f"\n[{index}] {row['title']} — {row['company']}")
        print(f"URL: {row['source_url']}")
        print(f"Remote: {row['remote_type']} | Seniority: {row['seniority']} | Role: {row['role']}")
        print(f"Languages: {row['language_requirements']}")
        print(f"Tech: {row['technology_requirements']}")
        print(f"Salary: {row['salary']}")


def collect_job_urls(args: argparse.Namespace) -> list[str]:
    if args.job_url:
        return [args.job_url]

    print(f"Fetching search page: {args.search_url}")
    search_html = fetch_text(args.search_url, args.user_agent)
    urls = extract_job_urls(search_html, args.search_url)
    if args.limit:
        urls = urls[: args.limit]
    return urls


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scan JustJoinIT vacancies into data/jobs.csv.")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--search-url", help="JustJoinIT search/listing URL.")
    source.add_argument("--job-url", help="Single JustJoinIT vacancy URL for debug parsing.")
    parser.add_argument("--csv", default="data/jobs.csv", help="Output CSV path.")
    parser.add_argument("--limit", type=int, default=10, help="Maximum vacancies from a search URL.")
    parser.add_argument(
        "--delay-seconds",
        type=float,
        default=30,
        help="Delay between vacancy page requests when scanning a search URL.",
    )
    parser.add_argument("--dry-run", action="store_true", help="Parse and print without writing CSV.")
    parser.add_argument("--user-agent", default=DEFAULT_USER_AGENT, help="HTTP User-Agent header.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    csv_path = Path(args.csv)
    known_urls = existing_urls(csv_path)
    job_urls = collect_job_urls(args)

    if not job_urls:
        print("No job URLs found.")
        return 0

    rows: list[dict[str, str]] = []
    for index, url in enumerate(job_urls, start=1):
        if url in known_urls:
            print(f"Skipping duplicate: {url}")
            continue

        if args.search_url and index > 1 and args.delay_seconds > 0:
            print(f"Sleeping {args.delay_seconds:g}s before next vacancy request...")
            time.sleep(args.delay_seconds)

        print(f"Fetching vacancy {index}/{len(job_urls)}: {url}")
        page_html = fetch_text(url, args.user_agent)
        row = parse_job_page(url, page_html)
        rows.append(row)
        known_urls.add(url)

    if not rows:
        print("No new vacancies to save.")
        return 0

    print_rows(rows)
    if args.dry_run:
        print(f"\nDry run: parsed {len(rows)} new vacancies, CSV was not changed.")
    else:
        append_rows(csv_path, rows)
        print(f"\nSaved {len(rows)} new vacancies to {csv_path}.")

    return 0


if __name__ == "__main__":
    sys.exit(main())
