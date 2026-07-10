#!/usr/bin/env python3
from __future__ import annotations

import argparse
from pathlib import Path
from urllib.parse import parse_qs
from urllib.parse import urlencode
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parent
SITE_URL = "https://justjoin.it"
API_URL = SITE_URL + "/api/candidate-api"
DEFAULT_ITEMS_COUNT = 100


def load_config(path: Path) -> dict[str, str]:
    config: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line and not line.startswith("#"):
            key, _, value = line.partition("=")
            config[key.strip()] = value.strip()
    if "mainTech" not in config:
        raise SystemExit(f"Config must define mainTech in {path}.")
    return config


def build_search_url(config: dict[str, str]) -> str:
    params = {
        "published-date": config.get("publishedDays"),
        "orderBy": config.get("orderBy", "DESC"),
        "sortBy": config.get("sortBy", "published"),
    }
    if config.get("languages"):
        params["languages"] = config["languages"]
    if config.get("experienceLevels"):
        params["experience-level"] = config["experienceLevels"]

    location = config.get("location", "all-locations")
    query = urlencode({key: value for key, value in params.items() if value})
    return f"{SITE_URL}/job-offers/{location}/{config['mainTech']}?{query}"


def build_api_params(config: dict[str, str], include_page: bool = False) -> dict:
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
            item.strip() for item in config["languages"].split(",") if item.strip()
        ]
    if config.get("experienceLevels"):
        params["experienceLevels"] = [
            item.strip()
            for item in config["experienceLevels"].split(",")
            if item.strip()
        ]
    if include_page:
        params["from"] = 0
        params["itemsCount"] = int(config.get("itemsCount", DEFAULT_ITEMS_COUNT))
    return params


def build_count_url(config: dict[str, str]) -> str:
    return API_URL + "/offers/count?" + urlencode(build_api_params(config), doseq=True)


def build_offers_url(config: dict[str, str], start: int = 0) -> str:
    params = build_api_params(config, include_page=True)
    params["from"] = start
    return API_URL + "/offers?" + urlencode(params, doseq=True)


def config_from_search_url(search_url: str) -> dict[str, str]:
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
        config["languages"] = ",".join(",".join(query["languages"]).split(","))
    if query.get("experience-level"):
        config["experienceLevels"] = ",".join(
            ",".join(query["experience-level"]).split(",")
        )
    if not config["mainTech"]:
        raise SystemExit(f"Could not infer mainTech from search URL: {search_url}")
    return config


def main() -> None:
    parser = argparse.ArgumentParser(description="Build JustJoinIT search/API URLs.")
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--config", default=str(ROOT / "config" / "justjoin.properties"))
    source.add_argument("--search-url")
    parser.add_argument("--from", dest="start", type=int, default=0)
    args = parser.parse_args()

    config = (
        config_from_search_url(args.search_url)
        if args.search_url
        else load_config(Path(args.config))
    )
    print("search\t" + build_search_url(config))
    print("count\t" + build_count_url(config))
    print("offers\t" + build_offers_url(config, args.start))


if __name__ == "__main__":
    main()
