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
from urllib.parse import urljoin, urlparse
from urllib.request import Request, urlopen


USER_AGENT = (
    "JobSeekerBot/0.1 "
    "(+https://github.com/Kryzhounik/JobSeeker; purpose=personal-job-research)"
)


def get_html(url: str) -> str:
    request = Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml",
            "Accept-Language": "en-US,en;q=0.8,ru;q=0.6",
        },
    )
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
    parser.add_argument("--search-url", help="JustJoinIT search page URL.")
    parser.add_argument("--job-url", help="Single vacancy URL for debug download.")
    parser.add_argument("--limit", type=int, default=10)
    parser.add_argument("--delay-seconds", type=float, default=30)
    parser.add_argument("--download", action="store_true", help="Download vacancy pages.")
    parser.add_argument("--out-dir", default="data/raw/justjoin")
    return parser.parse_args()


def main() -> None:
    args = parse_args()

    if args.job_url:
        urls = [args.job_url]
    elif args.search_url:
        urls = find_job_urls(args.search_url)[: args.limit]
    else:
        raise SystemExit("Use --search-url or --job-url.")

    for url in urls:
        print(url)

    if args.download:
        save_pages(urls, Path(args.out_dir), args.delay_seconds)


if __name__ == "__main__":
    main()
