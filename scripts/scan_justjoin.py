#!/usr/bin/env python3
"""Tiny JustJoinIT fetcher.

It only finds job URLs and optionally downloads job pages with a delay.
Parsing/categorization stays with Codex prompts, not in this script.
"""

from __future__ import annotations

import argparse
import re
import time
from html import unescape
from pathlib import Path
from urllib.parse import urlencode
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


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


def get_html(url: str) -> str:
    request = Request(url, headers=BROWSER_HEADERS)
    with urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def find_job_urls(search_url: str) -> list[str]:
    html = get_html(search_url)
    urls: list[str] = []
    seen: set[str] = set()

    for match in re.finditer(r"/job-offer/[^\"'<>\\\s]+", html):
        url = urljoin(search_url, unescape(match.group(0)).split("?", 1)[0])
        if url not in seen:
            seen.add(url)
            urls.append(url)

    return urls


def build_search_url(search: dict) -> str:
    params = {
        "published-date": search.get("publishedDays"),
        "orderBy": search.get("orderBy", "DESC"),
        "sortBy": search.get("sortBy", "published"),
    }
    languages = search.get("languages", "")
    if languages:
        params["languages"] = languages

    query = urlencode({key: value for key, value in params.items() if value})
    location = search.get("location", "all-locations")
    main_tech = search["mainTech"]
    return f"https://justjoin.it/job-offers/{location}/{main_tech}?{query}"


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


def save_pages(urls: list[str], out_dir: Path, delay_seconds: float) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)

    for index, url in enumerate(urls, start=1):
        if index > 1 and delay_seconds > 0:
            print(f"sleep {delay_seconds:g}s")
            time.sleep(delay_seconds)

        print(f"GET {url}")
        html = get_html(url)
        path = out_dir / page_name(url)
        path.write_text(html, encoding="utf-8")
        print(f"saved {path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Find/download JustJoinIT vacancy pages.")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--search-url", help="JustJoinIT search page URL.")
    source.add_argument("--job-url", help="Single vacancy URL for debug download.")
    parser.add_argument("--config", default="config/justjoin.properties")
    parser.add_argument("--limit", type=int)
    parser.add_argument("--delay-seconds", type=float)
    parser.add_argument("--download", action="store_true", help="Download vacancy pages.")
    parser.add_argument("--out-dir", default="data/raw/justjoin")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.job_url:
        urls = [args.job_url]
    else:
        config = load_config(Path(args.config))
        search_url = args.search_url or build_search_url(config)
        limit = args.limit or int(config.get("limit", 10))
        urls = find_job_urls(search_url)[:limit]

    for url in urls:
        print(url)

    if args.download:
        delay_seconds = args.delay_seconds
        if delay_seconds is None:
            delay_seconds = float(load_config(Path(args.config)).get("delaySeconds", 30))
        save_pages(urls, Path(args.out_dir), delay_seconds if delay_seconds is not None else 30)


if __name__ == "__main__":
    main()
