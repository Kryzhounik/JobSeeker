#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parent
SEARCH_URL = "https://www.linkedin.com/jobs/search-results/"
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


def location_key(raw_location: str) -> str:
    return raw_location.partition(":")[0].strip().lower().replace(" ", "_")


def is_locationless(raw_location: str) -> bool:
    return location_key(raw_location) in LOCATIONLESS


def load_config(path: Path) -> dict[str, str]:
    config: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#"):
            key, _, value = line.partition("=")
            config[key.strip()] = value.strip()
    return config


def csv(value: str) -> list[str]:
    return [part.strip() for part in value.split(",") if part.strip()]


def search_url(config: dict[str, str], raw_location: str) -> str:
    if config.get("searchUrl"):
        return config["searchUrl"]

    location, _, geo_id = raw_location.partition(":")
    location = location.strip()
    locationless = is_locationless(raw_location)
    params = {
        "keywords": config.get("keywords", ""),
        "origin": "JOB_SEARCH_PAGE_SEARCH_BUTTON",
        "refresh": "true",
    }
    if location and not locationless:
        params["location"] = location
        if geo_id.strip():
            params["geoId"] = geo_id.strip()

    date_posted = DATE_POSTED.get(config.get("datePosted", ""), "")
    if date_posted:
        params["f_TPR"] = date_posted
    return SEARCH_URL + "?" + urlencode(params)


def build_urls(config: dict[str, str]) -> list[tuple[str, str]]:
    locations = csv(config.get("locations", "")) or [""]
    result: list[tuple[str, str]] = []
    for index, raw_location in enumerate(locations):
        name = raw_location.partition(":")[0].strip() or "all"
        if is_locationless(raw_location) and index + 1 < len(locations):
            seed_location = locations[index + 1]
            seed_name = seed_location.partition(":")[0].strip()
            if seed_name and not is_locationless(seed_location):
                result.append((f"seed:{seed_name}", search_url(config, seed_location)))
        result.append((name, search_url(config, raw_location)))
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Build LinkedIn search URLs.")
    parser.add_argument("--config", default=str(ROOT / "config" / "linkedin.properties"))
    args = parser.parse_args()

    config = load_config(Path(args.config))
    for name, url in build_urls(config):
        print(f"{name}\t{url}")


if __name__ == "__main__":
    main()
