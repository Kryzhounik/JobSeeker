#!/usr/bin/env python3
"""Prefetch LinkedIn vacancies through the public guest endpoints.

The module exposes small diagnostic commands as well as the full batch:

    search-page  fetch and parse one result page
    job          fetch and process one vacancy through the content filter
    batch        collect until the configured number of analyzable texts exists

Collection stops at analyzer-ready text in SQLite. Analysis and scoring belong
to the top-level workflow.

This collector is not a complete replacement for logged-in browser collection.
LinkedIn's guest and authenticated searches can return different inventories,
counts, and ordering, and guest results can vary between repeated requests.
For that reason, missing ID overlap between adjacent guest responses is logged
as a warning and never stops collection by itself.
After the browser-only AccountRemote priority pass, use this module as the
cheap first pass for one country at a time: it stores
every vacancy it can reach and records its source job ID in SQLite with
`source_jobs.collection_method = script`. If this pass reaches the remaining
global limit, collection ends without a browser gap-fill. Otherwise the
browser collector fills only the remaining count for the same location; its
preview deduplication skips stored IDs before opening a job pane. Move to the
next configured location only while the combined scope remains below the
global limit.
"""

from __future__ import annotations

import argparse
from collections import Counter
from contextlib import redirect_stdout
from dataclasses import dataclass
from html.parser import HTMLParser
import json
from pathlib import Path
import sqlite3
import sys
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


COLLECTOR_ROOT = Path(__file__).resolve().parents[1]
DRIVER_ROOT = COLLECTOR_ROOT.parent
for import_root in (DRIVER_ROOT, COLLECTOR_ROOT):
    if str(import_root) not in sys.path:
        sys.path.insert(0, str(import_root))

from collector.extract_linkedin_readable_text_v2 import readable_text
from collector.filtering.linkedin_filter import VacancyFilter
from collector.filtering.linkedin_filter import apply_preview_decision_with_filter
from collector.filtering.linkedin_filter import content_response
from collector.filtering.linkedin_filter import load_config as load_filter_config
from collector.logging.linkedin_logger import record_collection_event
from collector.save_raw_page import save_content
from common.paths import DATA_ROOT
from db.companies import get_priority_linkedin_ids
from db.filter_rejections import save_content_filter_rejection
from db.migrate import migrate_database
from db.readable_text import save_readable_text


SEARCH_ENDPOINT = (
    "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search"
)
JOB_ENDPOINT = "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/{job_id}"
DEFAULT_CONFIG = COLLECTOR_ROOT / "config" / "linkedin.properties"
DEFAULT_DB = DATA_ROOT / "jobs.sqlite"
PAGE_STEP = 9
DATE_POSTED = {
    "day": "r86400",
    "week": "r604800",
    "month": "r2592000",
}
LOCATIONLESS = {
    "accountremote",
    "account_remote",
    "remote",
    "worldwide",
    "global",
    "anywhere",
}
HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/149.0.0.0 Safari/537.36"
    ),
    "Accept": "text/html,application/xhtml+xml",
    "Accept-Language": "en-US,en;q=0.8",
}


class CollectionError(RuntimeError):
    pass


class RateLimitError(CollectionError):
    pass


@dataclass(frozen=True)
class Location:
    label: str
    name: str = ""
    geo_id: str = ""


@dataclass
class Card:
    job_id: str
    title: str = ""
    company: str = ""
    location: str = ""
    salary: str = ""

    @property
    def source_url(self) -> str:
        return f"https://www.linkedin.com/jobs/view/{self.job_id}/"

    def preview(self, *, label: str, start: int, index: int) -> dict[str, Any]:
        return {
            "job_id": self.job_id,
            "source_url": self.source_url,
            "title": self.title,
            "company": self.company,
            "location": self.location,
            "workplace": "",
            "salary": self.salary,
            "label": label,
            "start": start,
            "index": index,
        }


class GuestCardParser(HTMLParser):
    VOID_TAGS = {
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    }
    FIELD_CLASSES = {
        "base-search-card__title": "title",
        "base-search-card__subtitle": "company",
        "job-search-card__location": "location",
        "job-search-card__salary-info": "salary",
    }

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.cards: list[Card] = []
        self.card: Card | None = None
        self.depth = 0
        self.capture_depths: dict[str, int] = {}
        self.parts: dict[str, list[str]] = {}

    def handle_starttag(
        self,
        tag: str,
        attrs: list[tuple[str, str | None]],
    ) -> None:
        attributes = dict(attrs)
        urn = attributes.get("data-entity-urn") or ""
        if self.card is None and urn.startswith("urn:li:jobPosting:"):
            self.card = Card(job_id=urn.rsplit(":", 1)[-1])
            self.depth = 0
            self.capture_depths = {}
            self.parts = {}
        if self.card is None:
            return

        if tag not in self.VOID_TAGS:
            self.depth += 1
        classes = set((attributes.get("class") or "").split())
        for class_name, field in self.FIELD_CLASSES.items():
            if class_name in classes:
                self.capture_depths[field] = self.depth
                self.parts[field] = []

    def handle_data(self, data: str) -> None:
        if self.card is None:
            return
        for field in self.capture_depths:
            self.parts[field].append(data)

    def handle_endtag(self, tag: str) -> None:
        if self.card is None:
            return
        if tag in self.VOID_TAGS:
            return
        for field, capture_depth in list(self.capture_depths.items()):
            if capture_depth == self.depth:
                value = " ".join(" ".join(self.parts[field]).split())
                setattr(self.card, field, value)
                del self.capture_depths[field]
        self.depth -= 1
        if self.depth == 0:
            self.cards.append(self.card)
            self.card = None


class HttpClient:
    def __init__(self, delay_seconds: float) -> None:
        self.delay_seconds = max(0.0, delay_seconds)
        self.last_request_started = 0.0

    def get(self, url: str) -> str:
        for attempt in range(2):
            self._wait()
            try:
                request = Request(url, headers=HEADERS)
                with urlopen(request, timeout=30) as response:
                    charset = response.headers.get_content_charset() or "utf-8"
                    return response.read().decode(charset, errors="replace")
            except HTTPError as error:
                if error.code == 429:
                    if attempt == 0:
                        continue
                    raise RateLimitError("LinkedIn returned HTTP 429") from error
                if error.code < 500 or attempt == 1:
                    raise CollectionError(f"LinkedIn returned HTTP {error.code}") from error
            except (URLError, TimeoutError) as error:
                if attempt == 1:
                    raise CollectionError(f"LinkedIn request failed: {error}") from error
        raise AssertionError("unreachable")

    def _wait(self) -> None:
        remaining = self.last_request_started + self.delay_seconds - time.monotonic()
        if remaining > 0:
            time.sleep(remaining)
        self.last_request_started = time.monotonic()


def load_settings(path: Path) -> dict[str, str]:
    settings: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#"):
            key, separator, value = line.partition("=")
            if not separator:
                raise ValueError(f"Invalid config line: {raw_line}")
            settings[key.strip()] = value.strip()
    return settings


def csv(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


def parse_location(raw: str) -> Location:
    label, _, geo_id = raw.partition(":")
    label = label.strip()
    key = label.lower().replace(" ", "_")
    return Location(
        label=label or "all",
        name="" if key in LOCATIONLESS else label,
        geo_id="" if key in LOCATIONLESS else geo_id.strip(),
    )


def search_page_url(
    settings: dict[str, str],
    location: Location,
    start: int,
    company_ids: list[str] | None = None,
) -> str:
    params = {
        "keywords": settings.get("keywords", ""),
        "start": str(start),
    }
    if location.name:
        params["location"] = location.name
    if location.geo_id:
        params["geoId"] = location.geo_id
    date_posted = DATE_POSTED.get(settings.get("datePosted", ""))
    if date_posted:
        params["f_TPR"] = date_posted
    if company_ids:
        params["f_C"] = ",".join(company_ids)
    return SEARCH_ENDPOINT + "?" + urlencode(params)


def parse_search_page(html: str) -> list[Card]:
    parser = GuestCardParser()
    parser.feed(html)
    return parser.cards


def page_overlap_missing(previous_ids: set[str], current_ids: set[str]) -> bool:
    return bool(
        previous_ids
        and current_ids
        and not previous_ids.intersection(current_ids)
    )


def should_stop_location(accepted_count: int, limit: int, no_new_pages: int) -> bool:
    return accepted_count >= limit or no_new_pages >= 2


def save_readable(
    card: Card,
    html: str,
    db_path: Path,
    raw_dir: Path,
    force: bool,
) -> str:
    with redirect_stdout(sys.stderr):
        raw_path = save_content(
            source="linkedin",
            url=card.source_url,
            content=html,
            out_dir=raw_dir,
            ext="html",
            force=force,
            db_path=db_path,
            collection_method="script",
        )
    text = readable_text("linkedin", raw_path)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        save_readable_text(connection, "linkedin", card.job_id, text)
        connection.commit()
    return text


def apply_content_filter(
    card: Card,
    text: str,
    vacancy_filter: VacancyFilter,
    db_path: Path,
) -> dict[str, Any]:
    result = vacancy_filter.filter_text(card.title, text)
    if result.rejected:
        with sqlite3.connect(db_path) as connection:
            connection.execute("PRAGMA foreign_keys = ON")
            save_content_filter_rejection(
                connection,
                "linkedin",
                card.job_id,
                rule=result.rule,
                matched_text=result.match,
                keyword_patterns=result.keyword_patterns,
            )
            connection.commit()
    return content_response(result)


def log_outcome(preview: dict[str, Any], status: str, reason: str, db_path: Path) -> None:
    record_collection_event(
        {**preview, "status": status, "reason": reason},
        db_path,
    )


def process_job(
    card: Card,
    preview: dict[str, Any],
    client: HttpClient,
    vacancy_filter: VacancyFilter,
    db_path: Path,
    raw_dir: Path,
    force: bool = False,
) -> dict[str, Any]:
    try:
        html = client.get(JOB_ENDPOINT.format(job_id=card.job_id))
        text = save_readable(card, html, db_path, raw_dir, force)
        decision = apply_content_filter(card, text, vacancy_filter, db_path)
    except RateLimitError:
        raise
    except (CollectionError, OSError, ValueError, SystemExit) as error:
        status = "incomplete_raw" if "incomplete" in str(error).lower() else "open_failed"
        log_outcome(preview, status, str(error), db_path)
        return {"job_id": card.job_id, "status": status, "reason": str(error)}

    analyze = decision["content_decision"] == "analyze"
    status = "raw_saved" if analyze else "content_filtered"
    log_outcome(preview, status, str(decision.get("content_reason", "")), db_path)
    return {
        "job_id": card.job_id,
        "title": card.title,
        "status": status,
        "reason": str(decision.get("content_reason", "")),
    }


def collect_batch(
    settings: dict[str, str],
    *,
    limit: int,
    client: HttpClient,
    db_path: Path,
    raw_dir: Path,
    location_value: str | None = None,
) -> dict[str, Any]:
    preview_config = load_filter_config(
        COLLECTOR_ROOT / "filtering" / "linkedin_preview_filter.ini"
    )
    vacancy_filter = VacancyFilter(db_path=db_path)
    connection = sqlite3.connect(db_path)
    try:
        priority_company_ids = get_priority_linkedin_ids(connection)
    finally:
        connection.close()
    search_passes = []
    if priority_company_ids:
        search_passes.append(("priority", priority_company_ids))
    search_passes.append(("general", []))
    seen_ids: set[str] = set()
    scope: list[dict[str, str]] = []
    outcomes: list[dict[str, Any]] = []
    pages: list[dict[str, Any]] = []

    configured_locations = (
        [location_value]
        if location_value is not None
        else csv(settings.get("locations", ""))
    )
    for location in map(parse_location, configured_locations):
        # AccountRemote in the shared search order belongs to the browser only.
        if location_value is None and not location.name:
            continue
        search_index = 0
        search_name, company_ids = search_passes[search_index]
        search_seen_ids: set[str] = set()
        previous_ids: set[str] = set()
        start = 0
        no_new_pages = 0
        while len(scope) < limit:
            cards = parse_search_page(
                client.get(search_page_url(settings, location, start, company_ids))
            )
            current_ids = {card.job_id for card in cards}
            search_new_ids = current_ids - search_seen_ids
            search_seen_ids.update(current_ids)
            overlap = len(previous_ids.intersection(current_ids))
            overlap_warning = ""
            if page_overlap_missing(previous_ids, current_ids):
                overlap_warning = (
                    f"LinkedIn pagination warning at {location.label} "
                    f"{search_name} start={start}: adjacent responses have no "
                    "overlapping job ID; continuing because guest results are "
                    "not stable between requests"
                )
                print(f"WARNING: {overlap_warning}", file=sys.stderr)
            new_cards = [card for card in cards if card.job_id not in seen_ids]
            page_result = {
                "label": location.label,
                "search": search_name,
                "start": start,
                "cards": len(cards),
                "new": len(new_cards),
                "overlap": overlap,
            }
            if overlap_warning:
                page_result["warning"] = overlap_warning
            pages.append(page_result)
            print(
                f"{location.label} {search_name} start={start} "
                f"cards={len(cards)} new={len(new_cards)} "
                f"accepted={len(scope)}/{limit}",
                file=sys.stderr,
            )
            no_new_pages = no_new_pages + 1 if not search_new_ids else 0
            for index, card in enumerate(new_cards, start=1):
                seen_ids.add(card.job_id)
                preview = card.preview(label=location.label, start=start, index=index)
                filtered = apply_preview_decision_with_filter(
                    preview,
                    preview_config,
                    vacancy_filter,
                    db_path,
                )
                if filtered["preview_decision"] == "skip":
                    outcomes.append(
                        {
                            "job_id": card.job_id,
                            "status": "preview_filtered",
                            "reason": filtered["preview_reason"],
                        }
                    )
                    continue

                outcome = process_job(
                    card,
                    preview,
                    client,
                    vacancy_filter,
                    db_path,
                    raw_dir,
                )
                outcomes.append(outcome)
                if outcome["status"] == "raw_saved":
                    scope.append(
                        {
                            "source": "linkedin",
                            "job_id": card.job_id,
                            "title": card.title,
                        }
                    )
                    if len(scope) >= limit:
                        break

            if should_stop_location(len(scope), limit, no_new_pages):
                search_index += 1
                if len(scope) >= limit or search_index >= len(search_passes):
                    break
                search_name, company_ids = search_passes[search_index]
                search_seen_ids = set()
                previous_ids = set()
                start = 0
                no_new_pages = 0
                continue
            previous_ids = current_ids
            start += PAGE_STEP

        if len(scope) >= limit:
            break

    return {
        "source": "linkedin",
        "accepted_count": len(scope),
        "scope": scope,
        "outcomes": dict(Counter(item["status"] for item in outcomes)),
        "pages": pages,
    }


def write_json(value: Any, output: str) -> None:
    text = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if output == "-":
        print(text, end="")
    else:
        Path(output).write_text(text, encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Collect LinkedIn vacancies.")
    commands = parser.add_subparsers(dest="command", required=True)

    def common(command: argparse.ArgumentParser) -> None:
        command.add_argument("--config", default=str(DEFAULT_CONFIG))
        command.add_argument("--db", default=str(DEFAULT_DB))
        command.add_argument("--delay-seconds", type=float)
        command.add_argument(
            "--raw-dir",
            default=str(DATA_ROOT / "raw" / "linkedin"),
        )
        command.add_argument("--output", default="-")

    page = commands.add_parser("search-page", help="Fetch and parse one search page.")
    common(page)
    page.add_argument("--location", required=True)
    page.add_argument("--start", type=int, default=0)

    job = commands.add_parser("job", help="Process one job through readable text.")
    common(job)
    job.add_argument("--job-id", required=True)
    job.add_argument("--title", default="")
    job.add_argument("--company", default="")
    job.add_argument("--force", action="store_true")

    batch = commands.add_parser("batch", help="Run the configured collection batch.")
    common(batch)
    batch.add_argument("--limit", type=int)
    batch.add_argument(
        "--location",
        help="Process exactly this Name or Name:geoId location.",
    )
    return parser


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    args = build_parser().parse_args()
    settings = load_settings(Path(args.config))
    delay = args.delay_seconds
    if delay is None:
        delay = float(settings.get("delaySeconds", "15"))
    client = HttpClient(delay)

    if args.command == "search-page":
        location = parse_location(args.location)
        cards = parse_search_page(
            client.get(search_page_url(settings, location, args.start))
        )
        write_json(
            [
                card.preview(label=location.label, start=args.start, index=index)
                for index, card in enumerate(cards, start=1)
            ],
            args.output,
        )
        return

    db_path = Path(args.db)
    raw_dir = Path(args.raw_dir)
    with redirect_stdout(sys.stderr):
        migrate_database(db_path)
    if args.command == "job":
        vacancy_filter = VacancyFilter(db_path=db_path)
        card = Card(args.job_id, args.title, args.company)
        preview = card.preview(label="debug", start=0, index=1)
        write_json(
            process_job(
                card,
                preview,
                client,
                vacancy_filter,
                db_path,
                raw_dir,
                args.force,
            ),
            args.output,
        )
        return

    limit = args.limit if args.limit is not None else int(settings.get("limit", "0"))
    if limit <= 0:
        raise SystemExit("batch limit must be positive")
    write_json(
        collect_batch(
            settings,
            limit=limit,
            client=client,
            db_path=db_path,
            raw_dir=raw_dir,
            location_value=args.location,
        ),
        args.output,
    )


if __name__ == "__main__":
    main()
