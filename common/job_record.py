from __future__ import annotations

from datetime import date
import re
from typing import Any


LANGUAGE_NAMES = {
    "en": "English",
    "pl": "Polish",
    "pt": "PT",
    "ru": "Russian",
    "uk": "Ukrainian",
    "ua": "Ukrainian",
    "de": "German",
    "fr": "French",
    "es": "Spanish",
    "it": "Italian",
}

COUNTRY_NAMES = {
    "PL": "Poland",
    "UA": "Ukraine",
    "US": "United States",
    "DE": "Germany",
    "GB": "United Kingdom",
    "CZ": "Czechia",
    "LT": "Lithuania",
    "LV": "Latvia",
    "EE": "Estonia",
    "MD": "Moldova",
    "GE": "Georgia",
    "RS": "Serbia",
    "NL": "Netherlands",
    "BE": "Belgium",
    "FR": "France",
    "ES": "Spain",
    "IT": "Italy",
    "PT": "Portugal",
    "RO": "Romania",
    "BG": "Bulgaria",
    "HU": "Hungary",
    "HR": "Croatia",
    "SK": "Slovakia",
    "SI": "Slovenia",
    "AT": "Austria",
    "CH": "Switzerland",
    "SE": "Sweden",
    "NO": "Norway",
    "FI": "Finland",
    "DK": "Denmark",
    "IE": "Ireland",
}

SKILL_LEVELS = {
    1: "nice to have",
    2: "junior",
    3: "regular",
    4: "advanced",
    5: "master",
}


def today_iso() -> str:
    return date.today().isoformat()


def clean(value: Any, default: str = "") -> str:
    text = str(value or "").strip()
    return text if text else default


def label_value(value: Any, default: str = "") -> str:
    if isinstance(value, dict):
        return clean(value.get("value") or value.get("label"), default)
    return clean(value, default)


def location_join(*parts: str) -> str:
    return ", ".join(part for part in parts if part and part != "-")


def remote_scope_from_country(remote_type: str, country: str, text: str = "") -> str:
    if remote_type != "remote":
        return ""

    normalized = text.lower()
    worldwide_markers = (
        "work anywhere",
        "any location worldwide",
        "work from any location",
        "globally remote",
        "worldwide",
    )
    if any(marker in normalized for marker in worldwide_markers):
        return "worldwide"

    return country or "unknown"


def normalize_remote_type(value: str, text: str = "") -> str:
    normalized = f"{value} {text}".lower()
    if "hybrid" in normalized or "гибрид" in normalized:
        return "hybrid"
    if (
        "remote" in normalized
        or "удален" in normalized
        or "удалён" in normalized
        or "zdaln" in normalized
    ):
        return "remote"
    if "on-site" in normalized or "onsite" in normalized or "office" in normalized:
        return "office"
    return clean(value, "unknown").lower()


def normalize_seniority(value: str) -> str:
    normalized = value.lower()
    if any(token in normalized for token in ("lead", "principal", "staff")):
        return "lead"
    if "senior" in normalized or "expert" in normalized:
        return "senior"
    if any(token in normalized for token in ("mid", "middle", "regular")):
        return "middle"
    if "junior" in normalized:
        return "junior"
    if "intern" in normalized:
        return "intern"
    return value or "unknown"


def infer_role(title: str, text: str = "") -> str:
    normalized = f"{title} {text}".lower()
    if "fullstack" in normalized or "full-stack" in normalized or "full stack" in normalized:
        return "fullstack"
    if "test" in normalized or "qa" in normalized or "sdet" in normalized:
        return "qa"
    if "devops" in normalized or "platform" in normalized or "sre" in normalized:
        return "devops"
    if "data" in normalized and "engineer" in normalized:
        return "data"
    if "machine learning" in normalized or " ai " in f" {normalized} ":
        return "ml_ai"
    if "frontend" in normalized or "front-end" in normalized:
        return "frontend"
    return "backend"


def salary_from_employment_types(employment_types: list[dict[str, Any]]) -> str:
    originals = [
        item for item in employment_types
        if item.get("currencySource") == "original"
    ]
    item = originals[0] if originals else (employment_types[0] if employment_types else None)
    if not item:
        return "unknown"

    currency = clean(item.get("currency"))
    unit = clean(item.get("unit"))
    contract = clean(item.get("type"))
    gross = item.get("gross")
    tax = "gross" if gross else "net" if gross is False else ""
    start = item.get("from")
    end = item.get("to")

    if start is None and end is None:
        amount = "unknown"
    elif start == end or end is None:
        amount = format_number(start)
    elif start is None:
        amount = format_number(end)
    else:
        amount = f"{format_number(start)}-{format_number(end)}"

    details = " ".join(part for part in [currency, tax, per_unit(unit), contract] if part)
    return " ".join(part for part in [amount, details] if part).strip() or "unknown"


def salary_from_text(text: str) -> str:
    pattern = re.compile(
        r"(?P<from>\d[\d\s.,]*)\s*(?:-|–|to)\s*(?P<to>\d[\d\s.,]*)\s*"
        r"(?P<currency>PLN|EUR|USD|GBP|CHF)",
        re.IGNORECASE,
    )
    match = pattern.search(text)
    if not match:
        return "unknown"
    return (
        f"{clean(match.group('from')).replace(' ', '')}-"
        f"{clean(match.group('to')).replace(' ', '')} "
        f"{match.group('currency').upper()}"
    )


def format_number(value: Any) -> str:
    if isinstance(value, float) and value.is_integer():
        return str(int(value))
    return str(value)


def per_unit(unit: str) -> str:
    if not unit:
        return ""
    return f"/{unit}"


def language_record(code_or_name: str, level: str) -> dict[str, str]:
    key = clean(code_or_name).lower()
    name = LANGUAGE_NAMES.get(key, clean(code_or_name))
    if len(name) == 2:
        name = name.upper()
    return {"name": name, "level": clean(level)}


def technology_record(
    name: str,
    requirement: str,
    level: str,
) -> dict[str, str]:
    tech_name = clean(name)
    tech_level = clean(level, "listed")
    if requirement == "nice_to_have":
        tech_level = "nice to have"
    return {
        "name": tech_name,
        "requirement": requirement,
        "level": tech_level,
        "raw_value": f"{tech_name}: {tech_level}",
    }


def skill_level(value: Any, default: str = "listed") -> str:
    return SKILL_LEVELS.get(value, clean(value, default))
