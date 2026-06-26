from __future__ import annotations

import re

from common.job_record import COUNTRY_NAMES
from common.job_record import clean
from common.job_record import language_record
from common.job_record import salary_from_text
from common.job_record import technology_record


COUNTRY_ALIASES = {
    name.lower(): name
    for name in COUNTRY_NAMES.values()
}
COUNTRY_ALIASES.update(
    {
        "polska": "Poland",
        "польша": "Poland",
        "украина": "Ukraine",
        "германия": "Germany",
        "молдова": "Moldova",
        "грузия": "Georgia",
        "сербия": "Serbia",
        "чехия": "Czechia",
        "литва": "Lithuania",
        "латвия": "Latvia",
        "эстония": "Estonia",
        "uk": "United Kingdom",
        "usa": "United States",
        "united states of america": "United States",
        "czech republic": "Czechia",
    }
)

LANGUAGE_ALIASES = {
    "English": ("english",),
    "Polish": ("polish", "polski"),
    "German": ("german", "deutsch"),
    "French": ("french",),
    "Spanish": ("spanish",),
    "Italian": ("italian",),
    "Russian": ("russian",),
    "Ukrainian": ("ukrainian",),
}

TECHNOLOGIES = (
    ("Java", (r"\bjava\b",)),
    ("Kotlin", (r"\bkotlin\b",)),
    ("Scala", (r"\bscala\b",)),
    ("Spring Boot", (r"\bspring\s+boot\b",)),
    ("Spring", (r"\bspring\b",)),
    ("Hibernate", (r"\bhibernate\b",)),
    ("Maven", (r"\bmaven\b",)),
    ("Gradle", (r"\bgradle\b",)),
    ("Kafka", (r"\bkafka\b", r"\bapache\s+kafka\b")),
    ("RabbitMQ", (r"\brabbitmq\b",)),
    ("AWS", (r"\baws\b", r"\bamazon\s+web\s+services\b")),
    ("Azure", (r"\bazure\b",)),
    ("GCP", (r"\bgcp\b", r"\bgoogle\s+cloud\b")),
    ("Docker", (r"\bdocker\b",)),
    ("Kubernetes", (r"\bkubernetes\b", r"\bk8s\b")),
    ("SQL", (r"\bsql\b",)),
    ("PostgreSQL", (r"\bpostgresql\b", r"\bpostgres\b")),
    ("MySQL", (r"\bmysql\b",)),
    ("Oracle", (r"\boracle\b",)),
    ("MongoDB", (r"\bmongodb\b",)),
    ("Redis", (r"\bredis\b",)),
    ("Microservices", (r"\bmicroservices?\b",)),
    ("REST API", (r"\brest(?:ful)?\b", r"\brest\s+api\b")),
    ("Git", (r"\bgit\b",)),
    ("Jenkins", (r"\bjenkins\b",)),
    ("GitLab", (r"\bgitlab\b",)),
    ("CI/CD", (r"\bci/cd\b", r"\bcontinuous\s+integration\b")),
    ("React", (r"\breact\b",)),
    ("Angular", (r"\bangular\b",)),
    ("TypeScript", (r"\btypescript\b",)),
    ("JavaScript", (r"\bjavascript\b",)),
    ("Python", (r"\bpython\b",)),
    ("C#", (r"\bc#\b", r"\bcsharp\b")),
)

OPTIONAL_MARKERS = (
    "nice to have",
    "nice-to-have",
    "will be a plus",
    "would be a plus",
    "is a plus",
    "as a plus",
    "bonus",
    "optional",
    "mile widziane",
)

REGULAR_MARKERS = (
    "hands-on",
    "hands on",
    "commercial experience",
    "production experience",
    "solid experience",
    "strong practical",
    "practical experience",
)

ADVANCED_MARKERS = (
    "advanced",
    "senior",
    "in-depth",
    "deep knowledge",
    "extensive experience",
    "strong knowledge",
)

MASTER_MARKERS = (
    "expert",
    "master",
)


def country_from_location(location: str) -> str:
    normalized = f" {location.lower()} "
    for alias, country in COUNTRY_ALIASES.items():
        if f" {alias} " in normalized or normalized.strip().endswith(f", {alias}"):
            return country
    return ""


def remote_scope_from_text(remote_type: str, location: str, text: str) -> str:
    if remote_type != "remote":
        return ""

    normalized = text.lower()
    if any(
        marker in normalized
        for marker in (
            "work anywhere",
            "any location worldwide",
            "work from any location",
            "work from any country",
            "globally remote",
            "worldwide",
        )
    ):
        return "worldwide"

    if re.search(r"\b(eu|european union)\b", normalized):
        return "EU"
    if re.search(r"\beurope\b", normalized):
        return "Europe"
    if re.search(r"\bemea\b", normalized):
        return "EMEA"

    return country_from_location(location) or "unknown"


def relocation_from_text(text: str, location: str = "") -> str:
    normalized = text.lower()
    if not any(
        marker in normalized
        for marker in (
            "relocation package",
            "relocation support",
            "relocation assistance",
            "relocation bonus",
            "visa sponsorship",
            "sponsorship visa",
            "paid relocation",
        )
    ):
        return "NO"

    countries = countries_from_text(text)
    if not countries:
        country = country_from_location(location)
        if country:
            countries = [country]

    return ", ".join(countries) if countries else "unknown"


def countries_from_text(text: str) -> list[str]:
    result: list[str] = []
    normalized = f" {text.lower()} "
    for alias, country in COUNTRY_ALIASES.items():
        if country in result:
            continue
        if f" {alias} " in normalized or f", {alias}" in normalized:
            result.append(country)
    return result


def languages_from_text(text: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    for name, aliases in LANGUAGE_ALIASES.items():
        match = first_alias_match(text, aliases)
        if not match:
            continue
        before = text[max(0, match.start() - 500) : match.start()].lower()
        line = current_line(text, match.start(), match.end()).lower()
        if is_optional(before) or is_optional(line):
            continue
        level = language_level_near(text, match.start(), match.end())
        result.append(language_record(name, level))
    return result


def current_line(text: str, start: int, end: int) -> str:
    line_start = text.rfind("\n", 0, start)
    line_end = text.find("\n", end)
    if line_start < 0:
        line_start = 0
    else:
        line_start += 1
    if line_end < 0:
        line_end = len(text)
    return text[line_start:line_end]


def first_alias_match(text: str, aliases: tuple[str, ...]) -> re.Match[str] | None:
    for alias in aliases:
        match = re.search(rf"\b{re.escape(alias)}\b", text, flags=re.I)
        if match:
            return match
    return None


def language_level_near(text: str, start: int, end: int) -> str:
    context = text[max(0, start - 80) : min(len(text), end + 80)].lower()
    cefr = re.search(r"\b(a1|a2|b1|b2|c1|c2)\b", context, flags=re.I)
    if cefr:
        return cefr.group(1).upper()
    if "native" in context:
        return "native"
    if "fluent" in context:
        return "fluent"
    if "advanced" in context or "excellent" in context:
        return "C1"
    if "upper-intermediate" in context or "communicative" in context:
        return "B2"
    if "intermediate" in context:
        return "B1"
    return ""


def technologies_from_text(text: str) -> list[dict[str, str]]:
    result: list[dict[str, str]] = []
    seen: set[str] = set()
    for name, patterns in TECHNOLOGIES:
        contexts = technology_contexts(text, patterns)
        if not contexts or name.lower() in seen:
            continue

        requirement = "nice_to_have" if any(is_optional(ctx) for ctx in contexts) else "required"
        level = "nice to have" if requirement == "nice_to_have" else best_level(contexts)
        result.append(technology_record(name, requirement, level))
        seen.add(name.lower())
    return result


def technology_contexts(text: str, patterns: tuple[str, ...]) -> list[str]:
    contexts: list[str] = []
    for pattern in patterns:
        for match in re.finditer(pattern, text, flags=re.I):
            start = max(0, match.start() - 180)
            end = min(len(text), match.end() + 180)
            contexts.append(text[start:end].lower())
    return contexts


def is_optional(context: str) -> bool:
    return any(marker in context for marker in OPTIONAL_MARKERS)


def best_level(contexts: list[str]) -> str:
    joined = "\n".join(contexts)
    if any(marker in joined for marker in MASTER_MARKERS):
        return "master"
    if any(marker in joined for marker in ADVANCED_MARKERS):
        return "advanced"
    if any(marker in joined for marker in REGULAR_MARKERS):
        return "regular"
    if re.search(r"\b[5-9]\+?\s+years?\b", joined):
        return "advanced"
    if re.search(r"\b[2-4]\+?\s+years?\b", joined):
        return "regular"
    return "junior"


def salary_from_raw_text(raw_salary: str, text: str) -> str:
    salary = clean(raw_salary)
    if salary:
        return salary
    return salary_from_text(text)
