"""Shared location-scope matching for analyzer evaluation modules."""

from __future__ import annotations

import re
from typing import Iterable


LOCATION_ALIASES = {
    "anywhere": "worldwide",
    "anywhere worldwide": "worldwide",
    "global": "worldwide",
    "globally": "worldwide",
    "world-wide": "worldwide",
    "worldwide": "worldwide",
    "emea": "emea",
    "europe": "europe",
    "european union": "eu",
    "eu": "eu",
    "central europe": "central europe",
    "southern europe": "southern europe",
    "czech republic": "czechia",
    "czechia": "czechia",
    "united kingdom": "united kingdom",
    "uk": "united kingdom",
    "usa": "united states",
    "us": "united states",
    "united states": "united states",
}
REGION_MEMBERS = {
    "emea": {
        "europe",
        "eu",
        "central europe",
        "southern europe",
        "ukraine",
        "moldova",
        "georgia",
        "serbia",
        "poland",
        "lithuania",
        "latvia",
        "czechia",
        "estonia",
        "united kingdom",
    },
    "europe": {
        "eu",
        "central europe",
        "southern europe",
        "ukraine",
        "moldova",
        "georgia",
        "serbia",
        "poland",
        "lithuania",
        "latvia",
        "czechia",
        "estonia",
        "united kingdom",
    },
    "eu": {"poland", "lithuania", "latvia", "czechia", "estonia"},
    "central europe": {"poland", "czechia"},
    "southern europe": {"serbia"},
    "americas": {"united states", "canada"},
}


def normalized(value: object) -> str:
    return str(value or "").strip().lower()


def split_match_values(value: object) -> list[str]:
    return [
        item.strip()
        for item in re.split(r"[,;/|]+|\bor\b|\band\b", normalized(value))
        if item.strip()
    ]


def canonical_location(value: object) -> str:
    text = normalized(value)
    text = re.sub(r"\([^)]*\)", "", text)
    text = re.sub(r"[^a-z0-9+ -]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return LOCATION_ALIASES.get(text, text)


def location_tokens(value: object) -> list[str]:
    raw = normalized(value)
    result: list[str] = []

    for item in [raw, *split_match_values(value)]:
        token = canonical_location(item)
        if token and token not in result:
            result.append(token)

    for alias, token in LOCATION_ALIASES.items():
        if re.search(rf"(^|\W){re.escape(alias)}($|\W)", raw) and token not in result:
            result.append(token)

    return result


def location_contains(container: str, item: str, seen: set[str] | None = None) -> bool:
    if not container or not item:
        return False
    if container == item:
        return True
    if container == "worldwide":
        return True

    visited = seen or set()
    if container in visited:
        return False
    visited.add(container)

    members = REGION_MEMBERS.get(container, set())
    if item in members:
        return True
    return any(location_contains(member, item, visited) for member in members)


def text_matches_allowed(value: object, allowed_values: Iterable[str]) -> bool:
    haystack = normalized(value)
    tokens = split_match_values(value)

    for allowed in allowed_values:
        needle = normalized(allowed)
        if not needle:
            continue
        if needle in tokens:
            return True
        if len(needle) > 2 and needle in haystack:
            return True

    return False


def check_location_allowance(possible: object, allowed_values: Iterable[str]) -> bool:
    """Return whether a vacancy scope contains any candidate-allowed location."""
    allowed = list(allowed_values)
    if text_matches_allowed(possible, allowed):
        return True

    possible_tokens = location_tokens(possible)
    allowed_tokens = [
        token
        for allowed_value in allowed
        for token in location_tokens(allowed_value)
    ]

    return any(
        location_contains(possible_token, allowed_token)
        for possible_token in possible_tokens
        for allowed_token in allowed_tokens
    )
