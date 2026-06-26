#!/usr/bin/env python3
"""Tiny JustJoinIT raw fetcher.

It finds job URLs and optionally downloads raw vacancy HTML with a delay.
Parsing/categorization stays with Codex prompts or a later separate step.
"""

from __future__ import annotations

import argparse
import json
import re
import time
from pathlib import Path
from urllib.parse import parse_qs
from urllib.parse import urlencode
from urllib.parse import urlparse
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]

SITE_URL = "https://justjoin.it"
BROWSER_API_URL = SITE_URL + "/api/candidate-api"
DEFAULT_ITEMS_COUNT = 100

BROWSER_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/149.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.8,ru;q=0.6",
    "Upgrade-Insecure-Requests": "1",
    "Sec-Fetch-Dest": "document",
    "Sec-Fetch-Mode": "navigate",
    "Sec-Fetch-Site": "none",
    "Sec-Fetch-User": "?1",
}


def project_root() -> Path:
    return ROOT


def collector_root() -> Path:
    return Path(__file__).resolve().parent


def get_text(url: str, headers: dict[str, str] | None = None) -> str:
    request = Request(url, headers=headers or BROWSER_HEADERS)
    with urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def get_html(url: str) -> str:
    return get_text(url)


def get_json(url: str, referer: str) -> dict:
    headers = {
        **BROWSER_HEADERS,
        "Accept": "application/json, text/plain, */*",
        "Referer": referer,
    }
    return json.loads(get_text(url, headers))


def find_job_urls_from_api(config: dict) -> list[str]:
    search_url = build_search_url(config)
    count_url = BROWSER_API_URL + "/offers/count?" + urlencode(
        build_api_params(config, include_page=False),
        doseq=True,
    )
    total = int(get_json(count_url, search_url)["count"])
    items_count = int(config.get("itemsCount", DEFAULT_ITEMS_COUNT))
    urls: list[str] = []
    seen: set[str] = set()

    for start in range(0, total, items_count):
        params = build_api_params(config, include_page=True)
        params["from"] = start
        params["itemsCount"] = items_count
        page_url = BROWSER_API_URL + "/offers?" + urlencode(params, doseq=True)
        data = get_json(page_url, search_url).get("data", [])

        for offer in data:
            slug = offer.get("slug")
            if not slug:
                continue
            url = SITE_URL + "/job-offer/" + slug
            if url not in seen:
                seen.add(url)
                urls.append(url)

        print(f"api page from={start} items={len(data)} total={total}")
        if not data:
            break

    return urls


def apply_limit(urls: list[str], limit: int) -> list[str]:
    if limit <= 0:
        return urls
    return urls[:limit]


def build_search_url(search: dict) -> str:
    params = {
        "published-date": search.get("publishedDays"),
        "orderBy": search.get("orderBy", "DESC"),
        "sortBy": search.get("sortBy", "published"),
    }
    languages = search.get("languages", "")
    if languages:
        params["languages"] = languages
    experience_levels = search.get("experienceLevels", "")
    if experience_levels:
        params["experience-level"] = experience_levels

    query = urlencode({key: value for key, value in params.items() if value})
    location = search.get("location", "all-locations")
    main_tech = search["mainTech"]
    return f"https://justjoin.it/job-offers/{location}/{main_tech}?{query}"


def build_api_params(config: dict, include_page: bool) -> dict:
    params: dict[str, str | list[str] | int] = {
        "categories": config["mainTech"],
        "orderBy": {"ASC": "ascending", "DESC": "descending"}.get(
            config.get("orderBy", "DESC"),
            "descending",
        ),
        "sortBy": {"published": "publishedAt"}.get(
            config.get("sortBy", "published"),
            config.get("sortBy", "published"),
        ),
    }

    if config.get("publishedDays"):
        params["publishedSinceDays"] = config["publishedDays"]
    if config.get("languages"):
        params["languages"] = [
            language.strip()
            for language in config["languages"].split(",")
            if language.strip()
        ]
    if config.get("experienceLevels"):
        params["experienceLevels"] = [
            level.strip()
            for level in config["experienceLevels"].split(",")
            if level.strip()
        ]
    if include_page:
        params["from"] = 0
        params["itemsCount"] = int(config.get("itemsCount", DEFAULT_ITEMS_COUNT))

    return params


def config_from_search_url(search_url: str) -> dict:
    parsed = urlparse(search_url)
    parts = [part for part in parsed.path.split("/") if part]
    query = parse_qs(parsed.query)

    config = {
        "location": parts[1] if len(parts) > 1 else "all-locations",
        "mainTech": parts[2] if len(parts) > 2 else "",
        "orderBy": query.get("orderBy", ["DESC"])[0],
        "sortBy": query.get("sortBy", ["published"])[0],
    }
    if query.get("published-date"):
        config["publishedDays"] = query["published-date"][0]
    if query.get("languages"):
        config["languages"] = ",".join(
            ",".join(query["languages"]).split(",")
        )
    if query.get("experience-level"):
        config["experienceLevels"] = ",".join(
            ",".join(query["experience-level"]).split(",")
        )
    if not config["mainTech"]:
        raise SystemExit(f"Could not infer mainTech from search URL: {search_url}")

    return config


def load_config(config_path: Path) -> dict:
    config: dict[str, str] = {}

    for raw_line in config_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue

        key, separator, value = line.partition("=")
        if separator:
            config[key.strip()] = value.strip()

    if "mainTech" not in config:
        raise SystemExit(f"Config must define mainTech in {config_path}.")

    return config


def page_name(url: str) -> str:
    slug = Path(urlparse(url).path).name or "job"
    return re.sub(r"[^a-zA-Z0-9_.-]+", "_", slug) + ".html"


def save_pages(urls: list[str], out_dir: Path, delay_seconds: float, force: bool) -> None:
    pages_dir = out_dir / "pages"
    pages_dir.mkdir(parents=True, exist_ok=True)

    for index, url in enumerate(urls, start=1):
        path = pages_dir / page_name(url)

        if path.exists() and not force:
            print(f"skip existing {index}/{len(urls)} {url}")
            continue

        if index > 1 and delay_seconds > 0:
            print(f"sleep {delay_seconds:g}s")
            time.sleep(delay_seconds)

        print(f"GET {index}/{len(urls)} {url}")
        html = get_html(url)
        path.write_text(html, encoding="utf-8")
        print(f"saved {path}")


def parse_args() -> argparse.Namespace:
    root = project_root()
    parser = argparse.ArgumentParser(description="Find/download JustJoinIT vacancy pages.")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--search-url", help="JustJoinIT search page URL.")
    source.add_argument("--job-url", help="Single vacancy URL for debug download.")
    parser.add_argument(
        "--config",
        default=str(collector_root() / "config" / "justjoin.properties"),
    )
    parser.add_argument("--limit", type=int)
    parser.add_argument("--delay-seconds", type=float)
    parser.add_argument("--download", action="store_true", help="Download raw vacancy pages.")
    parser.add_argument("--out-dir", default=str(root / "data" / "raw" / "justjoin"))
    parser.add_argument("--force", action="store_true", help="Re-download existing raw pages.")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.job_url:
        urls = [args.job_url]
    else:
        config = config_from_search_url(args.search_url) if args.search_url else load_config(Path(args.config))
        limit = args.limit if args.limit is not None else int(config.get("limit", 0))
        urls = apply_limit(find_job_urls_from_api(config), limit)

    print(f"found {len(urls)} job urls")
    for url in urls:
        print(url)

    if args.download:
        delay_seconds = args.delay_seconds
        if delay_seconds is None:
            delay_seconds = float(load_config(Path(args.config)).get("delaySeconds", 30))
        save_pages(
            urls,
            Path(args.out_dir),
            delay_seconds if delay_seconds is not None else 30,
            args.force,
        )


if __name__ == "__main__":
    main()
