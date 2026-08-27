#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parent
# /jobs/search/ is the primary browser search. /jobs/search-results/ is NOT
# equivalent: the same Moldova query showed 173 vs 3 results on 2026-08-27.
# Switch to the alternative only after confirming that the primary fails,
# not merely because its layout or selectors changed. See collector/README.md.
SEARCH_URL = "https://www.linkedin.com/jobs/search/"
EXPERIENCE = {"associate": "3", "mid_senior": "4"}
WORKPLACE = {"office": "1", "remote": "2", "hybrid": "3"}
JOB_TYPES = {"full_time": "F", "part_time": "P", "contract": "C"}
SORT = {"newest": "DD", "relevant": "R"}
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


def mapped(value: str, mapping: dict[str, str]) -> str:
    result: list[str] = []
    for item in csv(value):
        key = item.lower().replace("-", "_").replace(" ", "_")
        result.append(mapping.get(key, item))
    return ",".join(result)


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

    workplace = (
        config.get("remoteBroadWorkplace", "remote")
        if locationless
        else config.get("workplace", "")
    )
    values = {
        "f_E": mapped(config.get("experience", ""), EXPERIENCE),
        "f_WT": mapped(workplace, WORKPLACE),
        "f_JT": mapped(config.get("jobTypes", ""), JOB_TYPES),
        "f_TPR": DATE_POSTED.get(config.get("datePosted", ""), ""),
        "sortBy": SORT.get(config.get("sort", ""), config.get("sort", "")),
    }
    params.update({key: value for key, value in values.items() if value})
    return SEARCH_URL + "?" + urlencode(params)


def build_urls(config: dict[str, str]) -> list[tuple[str, str]]:
    locations = csv(config.get("locations", "")) or [""]
    anchor = next((item for item in locations if item and not is_locationless(item)), "")
    result: list[tuple[str, str]] = []
    for raw_location in locations:
        name = raw_location.partition(":")[0].strip() or "all"
        if is_locationless(raw_location) and anchor:
            # The browser submits this seed once with Search to remember its country.
            seed_config = {
                **config, "workplace": config.get("remoteBroadWorkplace", "remote"),
            }
            seed_name = anchor.partition(":")[0].strip()
            result.append((f"seed:{seed_name}", search_url(seed_config, anchor)))
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
