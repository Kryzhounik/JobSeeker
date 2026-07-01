#!/usr/bin/env python3
"""Tiny JustJoinIT raw fetcher.

It finds job URLs and optionally downloads raw vacancy HTML with a delay.
Parsing/categorization stays with Codex prompts or a later separate step.
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

from justjoin_search_urls import API_URL as BROWSER_API_URL
from justjoin_search_urls import DEFAULT_ITEMS_COUNT
from justjoin_search_urls import SITE_URL
from justjoin_search_urls import build_api_params
from justjoin_search_urls import build_search_url
from justjoin_search_urls import config_from_search_url
from justjoin_search_urls import load_config
from save_raw_page import save_content


ROOT = Path(__file__).resolve().parents[1]

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


def save_pages(urls: list[str], out_dir: Path, delay_seconds: float, force: bool) -> None:
    for index, url in enumerate(urls, start=1):
        if index > 1 and delay_seconds > 0:
            print(f"sleep {delay_seconds:g}s")
            time.sleep(delay_seconds)

        print(f"GET {index}/{len(urls)} {url}")
        html = get_html(url)
        save_content(
            source="justjoin",
            url=url,
            content=html,
            out_dir=out_dir,
            ext="html",
            force=force,
        )


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
