#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parents[1]
LINKEDIN_SEARCH_URL = "https://www.linkedin.com/jobs/search/"

EXPERIENCE = {
    "internship": "1",
    "entry": "2",
    "associate": "3",
    "mid_senior": "4",
    "director": "5",
    "executive": "6",
}
WORKPLACE = {
    "onsite": "1",
    "on_site": "1",
    "office": "1",
    "remote": "2",
    "hybrid": "3",
}
JOB_TYPES = {
    "full_time": "F",
    "part_time": "P",
    "contract": "C",
    "temporary": "T",
    "internship": "I",
    "other": "O",
}
DATE_POSTED = {
    "day": "r86400",
    "week": "r604800",
    "month": "r2592000",
}
SORT = {
    "newest": "DD",
    "relevant": "R",
}
LOCATIONLESS_REMOTE = {
    "accountremote",
    "account_remote",
    "worldwide",
    "global",
    "global_remote",
    "remote",
    "anywhere",
}


def collector_root() -> Path:
    return Path(__file__).resolve().parent


def load_config(path: Path) -> dict[str, str]:
    config: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue

        key, separator, value = line.partition("=")
        if separator:
            config[key.strip()] = value.strip()
    return config


def csv_values(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def mapped_csv(value: str, mapping: dict[str, str]) -> str:
    result: list[str] = []
    for item in csv_values(value):
        key = item.lower().replace("-", "_").replace(" ", "_")
        result.append(mapping.get(key, item))
    return ",".join(result)


def parse_location(value: str) -> tuple[str, str]:
    name, separator, geo_id = value.partition(":")
    return name.strip(), geo_id.strip() if separator else ""


def build_search_url(config: dict[str, str], location: str, geo_id: str) -> str:
    if config.get("searchUrl"):
        return config["searchUrl"]

    params = {
        "keywords": config.get("keywords", ""),
        "origin": "JOB_SEARCH_PAGE_SEARCH_BUTTON",
        "refresh": "true",
    }
    if location and location.lower().replace(" ", "_") not in LOCATIONLESS_REMOTE:
        params["location"] = location
    if geo_id and "location" in params:
        params["geoId"] = geo_id

    experience = mapped_csv(config.get("experience", ""), EXPERIENCE)
    if experience:
        params["f_E"] = experience

    workplace = mapped_csv(config.get("workplace", ""), WORKPLACE)
    if workplace:
        params["f_WT"] = workplace

    job_types = mapped_csv(config.get("jobTypes", ""), JOB_TYPES)
    if job_types:
        params["f_JT"] = job_types

    date_posted = DATE_POSTED.get(config.get("datePosted", "").lower(), config.get("datePosted", ""))
    if date_posted:
        params["f_TPR"] = date_posted

    sort = SORT.get(config.get("sort", "").lower(), config.get("sort", ""))
    if sort:
        params["sortBy"] = sort

    return LINKEDIN_SEARCH_URL + "?" + urlencode(params)


def build_urls(config: dict[str, str]) -> list[tuple[str, str]]:
    locations = csv_values(config.get("locations", ""))
    if not locations:
        locations = [""]

    result: list[tuple[str, str]] = []
    for raw_location in locations:
        location, geo_id = parse_location(raw_location)
        result.append((location or "all", build_search_url(config, location, geo_id)))
    return result


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build LinkedIn search URLs from config.")
    parser.add_argument(
        "--config",
        default=str(collector_root() / "config" / "linkedin.properties"),
    )
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    config = load_config(Path(args.config))
    for location, url in build_urls(config):
        print(f"{location}: {url}")


if __name__ == "__main__":
    main()
