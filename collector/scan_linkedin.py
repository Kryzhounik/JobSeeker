#!/usr/bin/env python3
from __future__ import annotations

import argparse
import html
import json
import re
import time
from pathlib import Path
from urllib.parse import parse_qsl
from urllib.parse import urlencode
from urllib.parse import urlparse
from urllib.parse import urlunparse
from urllib.request import Request
from urllib.request import urlopen

from linkedin_search_urls import build_urls
from linkedin_search_urls import collector_root
from linkedin_search_urls import load_config


ROOT = Path(__file__).resolve().parents[1]

BROWSER_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/149.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "en-US,en;q=0.8,ru;q=0.6",
}


def get_text(url: str) -> str:
    request = Request(url, headers=BROWSER_HEADERS)
    with urlopen(request, timeout=30) as response:
        charset = response.headers.get_content_charset() or "utf-8"
        return response.read().decode(charset, errors="replace")


def with_start(url: str, start: int) -> str:
    parsed = urlparse(url)
    query = dict(parse_qsl(parsed.query, keep_blank_values=True))
    if start > 0:
        query["start"] = str(start)
    else:
        query.pop("start", None)
    return urlunparse(parsed._replace(query=urlencode(query)))


def canonical_job_url(raw_url: str) -> str:
    match = re.search(r"(?:currentJobId=|/jobs/view/(?:[^/?#]+-)?)(\d{7,})", raw_url)
    if not match:
        return ""
    return f"https://www.linkedin.com/jobs/view/{match.group(1)}/"


def extract_job_urls(search_html: str) -> list[str]:
    urls: list[str] = []
    seen: set[str] = set()
    for raw_url in re.findall(r'https?://[^"\'<>\s]+|/jobs/view/[^"\'<>\s]+', search_html):
        url = html.unescape(raw_url)
        canonical = canonical_job_url(url)
        if canonical and canonical not in seen:
            seen.add(canonical)
            urls.append(canonical)
    return urls


def find_job_urls(
    config: dict[str, str],
    limit: int,
    page_step: int,
    max_start: int,
) -> tuple[list[dict[str, str]], list[dict[str, object]]]:
    jobs: list[dict[str, str]] = []
    seen: set[str] = set()
    stats: list[dict[str, object]] = []

    for location, base_url in build_urls(config):
        if len(jobs) >= limit:
            break

        location_stat: dict[str, object] = {
            "location": location,
            "added": 0,
            "total": len(jobs),
            "pages": [],
        }
        empty_pages = 0

        for start in range(0, max_start + 1, page_step):
            if len(jobs) >= limit:
                break

            search_url = with_start(base_url, start)
            search_html = get_text(search_url)
            page_urls = extract_job_urls(search_html)
            before = len(jobs)

            for url in page_urls:
                if url in seen:
                    continue
                seen.add(url)
                jobs.append(
                    {
                        "url": url,
                        "location": location,
                        "pageStart": str(start),
                    }
                )
                if len(jobs) >= limit:
                    break

            added = len(jobs) - before
            location_stat["added"] = int(location_stat["added"]) + added
            location_stat["total"] = len(jobs)
            location_stat["pages"].append(
                {
                    "start": start,
                    "added": added,
                    "seenOnPage": len(page_urls),
                    "total": len(jobs),
                }
            )
            print(f"{location} start={start} page={len(page_urls)} added={added} total={len(jobs)}")

            empty_pages = empty_pages + 1 if added == 0 else 0
            if empty_pages >= 2:
                break

        stats.append(location_stat)

    return jobs, stats


def save_queue(
    jobs: list[dict[str, str]],
    stats: list[dict[str, object]],
    out_dir: Path,
    limit: int,
    page_step: int,
) -> None:
    out_dir.mkdir(parents=True, exist_ok=True)
    (out_dir / "queue.json").write_text(
        json.dumps(
            {
                "limit": limit,
                "paginationStep": page_step,
                "perLocation": stats,
                "jobs": jobs,
            },
            indent=2,
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )


def raw_page_name(url: str) -> str:
    match = re.search(r"/jobs/view/(\d+)/", url)
    return f"{match.group(1) if match else 'linkedin-job'}.html"


def download_jobs(
    jobs: list[dict[str, str]],
    out_dir: Path,
    delay_seconds: float,
    force: bool,
) -> None:
    pages_dir = out_dir / "pages"
    pages_dir.mkdir(parents=True, exist_ok=True)

    for index, job in enumerate(jobs, start=1):
        url = job["url"]
        path = pages_dir / raw_page_name(url)
        if path.exists() and not force:
            print(f"skip existing {index}/{len(jobs)} {url}")
            continue

        if index > 1 and delay_seconds > 0:
            print(f"sleep {delay_seconds:g}s")
            time.sleep(delay_seconds)

        print(f"GET {index}/{len(jobs)} {url}")
        page_html = get_text(url)
        path.write_text(page_html, encoding="utf-8")
        print(f"saved {path}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Find/download LinkedIn vacancy pages.")
    parser.add_argument(
        "--config",
        default=str(collector_root() / "config" / "linkedin.properties"),
    )
    parser.add_argument("--limit", type=int)
    parser.add_argument("--page-step", type=int, default=25)
    parser.add_argument("--max-start", type=int, default=500)
    parser.add_argument("--download", action="store_true")
    parser.add_argument(
        "--download-existing",
        action="store_true",
        help="Download jobs from existing queue.json without rebuilding the queue.",
    )
    parser.add_argument("--download-count", type=int, default=0)
    parser.add_argument("--delay-seconds", type=float)
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--out-dir", default=str(ROOT / "data" / "raw" / "linkedin"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = load_config(Path(args.config))
    limit = args.limit if args.limit is not None else int(config.get("limit", 0) or 0)
    if limit <= 0:
        limit = 100

    out_dir = Path(args.out_dir)
    if args.download_existing:
        queue_path = out_dir / "queue.json"
        queue = json.loads(queue_path.read_text(encoding="utf-8"))
        jobs = queue.get("jobs", [])
        print(f"loaded {len(jobs)} job urls from {queue_path}")
    else:
        jobs, stats = find_job_urls(
            config=config,
            limit=limit,
            page_step=args.page_step,
            max_start=args.max_start,
        )
        save_queue(jobs, stats, out_dir, limit, args.page_step)

        print(f"found {len(jobs)} unique job urls")
        for job in jobs:
            print(job["url"])

    if args.download_count > 0:
        jobs = jobs[: args.download_count]

    if args.download or args.download_existing:
        delay_seconds = args.delay_seconds
        if delay_seconds is None:
            delay_seconds = float(config.get("delaySeconds", 15) or 15)
        download_jobs(jobs, out_dir, delay_seconds, args.force)


if __name__ == "__main__":
    main()
