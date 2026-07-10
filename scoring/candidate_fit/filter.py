"""Fast candidate-fit filter.

This answers "can this candidate consider this job at all?" using deterministic
rules from config/resume. Deeper resume matching belongs in evaluate.md/Codex.
"""

from __future__ import annotations

from dataclasses import dataclass
import configparser
from pathlib import Path
import re
from typing import Any, Iterable


LANGUAGE_RANKS = {
    "a1": 1,
    "a2": 2,
    "b1": 3,
    "b2": 4,
    "c1": 5,
    "c2": 6,
    "native": 6,
    "fluent": 6,
}
NO_VALUES = {"", "no", "none", "unknown", "n/a", "-"}
PROGRAMMING_LANGUAGE_ALIASES = {
    "c": "c",
    "c sharp": "c#",
    "c#": "c#",
    "cpp": "c++",
    "c++": "c++",
    "go": "go",
    "golang": "go",
    "java": "java",
    "javascript": "javascript",
    "js": "javascript",
    "kotlin": "kotlin",
    "node": "javascript",
    "node js": "javascript",
    "node.js": "javascript",
    "php": "php",
    "python": "python",
    "ruby": "ruby",
    "rust": "rust",
    "scala": "scala",
    "typescript": "typescript",
    "ts": "typescript",
}
KOTLIN_JVM_FALLBACK = "java"
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
TIMEZONE_MARKERS = (
    "timezone",
    "time zone",
    "utc",
    "gmt",
    "cet",
    "cest",
    "eet",
    "uk time",
)


@dataclass(frozen=True)
class FilterResult:
    passed: bool
    candidate_fit_percent: int
    reason: str


def project_root() -> Path:
    return Path(__file__).resolve().parents[2]


def default_resume_path() -> Path:
    return project_root() / "scoring" / "candidate_fit" / "config" / "resume.ini"


def default_filter_path() -> Path:
    return project_root() / "scoring" / "candidate_fit" / "config" / "filter.ini"


def load_filter_config(path: Path | None = None) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path or default_filter_path(), encoding="utf-8")
    return config


def load_resume_config(path: Path | None = None) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path or default_resume_path(), encoding="utf-8")
    return config


def load_resume(path: Path | None = None) -> dict[str, int]:
    config = load_resume_config(path)
    result: dict[str, int] = {}
    if not config.has_section("languages"):
        return result

    for language, level in config.items("languages"):
        result[language.lower()] = language_rank(level)
    return result


def load_technology_levels(path: Path | None = None) -> dict[str, int]:
    config = load_resume_config(path)
    result: dict[str, int] = {}
    if not config.has_section("technology_levels"):
        return result

    for technology, level in config.items("technology_levels"):
        canonical = canonical_programming_language(technology)
        if canonical:
            result[canonical] = int_value(level, 0)
    return result


def language_rank(level: object) -> int:
    text = str(level or "").strip().lower()
    for token, rank in LANGUAGE_RANKS.items():
        if token in text:
            return rank
    return 0


def enabled(
    config: configparser.ConfigParser,
    section: str,
    key: str,
    default: bool,
) -> bool:
    try:
        return config.getboolean(section, key, fallback=default)
    except ValueError:
        return default


def filter_mode(
    config: configparser.ConfigParser,
    section: str,
    key: str,
    default: str,
) -> str:
    value = setting(config, section, key, default).strip().lower()
    if value in {"off", "false", "no", "0"}:
        return "off"
    if value in {"on", "true", "yes", "1", "any"}:
        return "on"
    if value in {"location", "locations", "loc"}:
        return "location"
    return default


def setting(
    config: configparser.ConfigParser,
    section: str,
    key: str,
    default: str = "",
) -> str:
    if not config.has_section(section):
        return default
    return config.get(section, key, fallback=default)


def csv_values(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def int_value(value: object, default: int = 0) -> int:
    try:
        return int(value or default)
    except (TypeError, ValueError):
        return default


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


def matches_remote_scope(scope: object, allowed_values: Iterable[str]) -> bool:
    allowed = list(allowed_values)
    if text_matches_allowed(scope, allowed):
        return True

    scope_tokens = location_tokens(scope)
    allowed_tokens = [
        token
        for allowed_value in allowed
        for token in location_tokens(allowed_value)
    ]

    return any(
        location_contains(scope_token, allowed_token)
        for scope_token in scope_tokens
        for allowed_token in allowed_tokens
    )


def is_timezone_scope(value: object) -> bool:
    text = normalized(value)
    return any(marker in text for marker in TIMEZONE_MARKERS)


def matches_allowed(value: object, allowed_values: Iterable[str]) -> bool:
    allowed = list(allowed_values)
    if text_matches_allowed(value, allowed):
        return True

    value_tokens = location_tokens(value)
    allowed_tokens = [
        token
        for allowed_value in allowed
        for token in location_tokens(allowed_value)
    ]

    return any(
        location_contains(value_token, allowed_token)
        or location_contains(allowed_token, value_token)
        for value_token in value_tokens
        for allowed_token in allowed_tokens
    )


def canonical_programming_language(value: object) -> str:
    text = normalized(value)
    text = text.replace("_", " ")
    text = re.sub(r"[^a-z0-9+#.]+", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return PROGRAMMING_LANGUAGE_ALIASES.get(text, "")


def programming_language_options(value: object) -> list[str]:
    result: list[str] = []
    for item in split_match_values(value):
        language = canonical_programming_language(item)
        if language and language not in result:
            result.append(language)
    language = canonical_programming_language(value)
    if language and language not in result:
        result.append(language)
    return result


def technology_requirement_type(row: Any) -> str:
    value = normalized(row_value(row, "requirement", "requirement_type"))
    if value in {"nice_to_have", "nice to have", "optional", "opt"}:
        return "nice_to_have"
    return "required"


def programming_language_level(
    language: str,
    technology_levels: dict[str, int],
) -> int:
    if language == "kotlin":
        return max(
            technology_levels.get("kotlin", 0),
            technology_levels.get(KOTLIN_JVM_FALLBACK, 0),
        )
    return technology_levels.get(language, 0)


def evaluate_required_programming_languages(
    technologies: Iterable[Any],
    technology_levels: dict[str, int] | None = None,
    resume_path: Path | None = None,
) -> FilterResult:
    levels = technology_levels if technology_levels is not None else load_technology_levels(resume_path)

    for row in technologies:
        if technology_requirement_type(row) != "required":
            continue

        required_rank = int_value(row_value(row, "level_rank"), 0)
        if required_rank <= 2:
            continue

        name = row_value(row, "name", "technology")
        options = programming_language_options(name)
        if not options:
            continue

        if any(programming_language_level(option, levels) >= required_rank for option in options):
            continue

        label = str(name or "").strip()
        return FilterResult(
            False,
            0,
            f"required programming language missing: {label} rank {required_rank}",
        )

    return FilterResult(True, 100, "programming language filter passed")


def evaluate_title(title: str, blocked_terms: Iterable[str] = ()) -> FilterResult:
    normalized_title = title.strip().lower()
    for term in blocked_terms:
        normalized_term = term.strip().lower()
        if normalized_term and normalized_term in normalized_title:
            return FilterResult(False, 0, f"title blocked by term: {term}")
    return FilterResult(True, 100, "title filter passed")


def evaluate_required_languages(
    required_languages: Iterable[Any],
    resume_languages: dict[str, int] | None = None,
    resume_path: Path | None = None,
) -> FilterResult:
    resume = resume_languages if resume_languages is not None else load_resume(resume_path)
    english_limit = LANGUAGE_RANKS["b2"]

    for row in required_languages:
        language = str(row_value(row, "language", "name") or "").strip()
        if not language:
            continue

        level = row_value(row, "level")
        required_rank = int(row_value(row, "level_rank") or language_rank(level))
        key = language.lower()

        if key == "english" and required_rank > english_limit:
            return FilterResult(False, 0, f"English above B2 required: {level}")

        own_rank = resume.get(key)
        if own_rank is None:
            return FilterResult(False, 0, f"Required language not in resume: {language}")

        if required_rank and own_rank < required_rank:
            return FilterResult(False, 0, f"{language} required {level}, resume lower")

    return FilterResult(True, 100, "language filter passed")


def evaluate_remote(
    remote_type: str,
    remote_scope: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
    remote_mode: str,
) -> FilterResult:
    if remote_mode == "off":
        return FilterResult(True, 100, "remote filter disabled")

    if normalized(remote_type) != "remote":
        return FilterResult(False, 0, f"remote filter failed: {remote_type or 'empty'}")

    scope = str(remote_scope or "").strip()
    if remote_mode == "on":
        return FilterResult(True, 100, f"remote filter passed: {scope or 'remote'}")

    if normalized(scope) in NO_VALUES:
        return FilterResult(False, 0, "remote filter failed: remote scope is empty")

    if is_timezone_scope(scope):
        return FilterResult(True, 100, f"remote timezone scope accepted: {scope}")

    allowed = csv_values(setting(resume_config, "remote", "allowed_scopes"))
    if matches_remote_scope(scope, allowed):
        return FilterResult(True, 100, f"remote filter passed: {scope}")

    return FilterResult(False, 0, f"remote filter failed: {scope}")


def evaluate_relocation(
    relocation: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
    relocation_mode: str,
) -> FilterResult:
    if relocation_mode == "off":
        return FilterResult(True, 100, "relocation filter disabled")

    destination = str(relocation or "").strip()
    if normalized(destination) in NO_VALUES:
        return FilterResult(False, 0, "relocation filter failed: NO")

    if relocation_mode == "on":
        return FilterResult(True, 100, f"relocation filter passed: {destination}")

    allowed = csv_values(setting(resume_config, "relocation", "allowed_destinations"))
    if matches_allowed(destination, allowed):
        return FilterResult(True, 100, f"relocation filter passed: {destination}")

    return FilterResult(False, 0, f"relocation filter failed: {destination}")


def evaluate_remote_or_relocation(
    remote_type: str,
    remote_scope: str,
    relocation: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
) -> FilterResult:
    checks: list[FilterResult] = []
    remote_mode = filter_mode(config, "filters", "remote", "off")
    relocation_mode = filter_mode(config, "filters", "relocation", "off")

    if remote_mode != "off":
        checks.append(
            evaluate_remote(
                remote_type,
                remote_scope,
                config,
                resume_config,
                remote_mode,
            )
        )
    if relocation_mode != "off":
        checks.append(evaluate_relocation(relocation, config, resume_config, relocation_mode))

    if not checks:
        return FilterResult(True, 100, "remote/relocation filters disabled")

    mode = setting(config, "filters", "remote_relocation_mode", "any").lower()
    if mode == "all":
        if all(result.passed for result in checks):
            return FilterResult(True, 100, "remote/relocation filters passed")
        return FilterResult(
            False,
            0,
            "; ".join(result.reason for result in checks if not result.passed),
        )

    for result in checks:
        if result.passed:
            return result

    return FilterResult(False, 0, "; ".join(result.reason for result in checks))


def evaluate_job(
    *,
    title: str = "",
    required_languages: Iterable[Any] = (),
    technologies: Iterable[Any] = (),
    remote_type: str = "",
    remote_scope: str = "",
    relocation: str = "",
    resume_path: Path | None = None,
    filter_path: Path | None = None,
) -> FilterResult:
    config = load_filter_config(filter_path)
    resume_config = load_resume_config(resume_path)

    if enabled(config, "filters", "title", False):
        blocked_terms = csv_values(setting(config, "title", "blocked_terms"))
        title_result = evaluate_title(title, blocked_terms)
        if not title_result.passed:
            return title_result

    if enabled(config, "filters", "languages", True):
        language_result = evaluate_required_languages(
            required_languages,
            resume_languages=load_resume(resume_path),
        )
        if not language_result.passed:
            return language_result

    if enabled(config, "filters", "programming_languages", True):
        programming_language_result = evaluate_required_programming_languages(
            technologies,
            technology_levels=load_technology_levels(resume_path),
        )
        if not programming_language_result.passed:
            return programming_language_result

    logistics_result = evaluate_remote_or_relocation(
        remote_type,
        remote_scope,
        relocation,
        config,
        resume_config,
    )
    if not logistics_result.passed:
        return logistics_result

    return FilterResult(True, 100, "job filter passed")


def filter_job_json(
    record: dict[str, Any],
    *,
    resume_path: Path | None = None,
    filter_path: Path | None = None,
) -> FilterResult:
    """Evaluate one analyzed job JSON through the complete fast filter."""
    return evaluate_job(
        title=str(record.get("title") or ""),
        required_languages=record.get("languages", []),
        technologies=record.get("technologies", []),
        remote_type=str(record.get("remote_type") or ""),
        remote_scope=str(record.get("remote_scope") or ""),
        relocation=str(record.get("relocation") or ""),
        resume_path=resume_path,
        filter_path=filter_path,
    )


def row_value(row: Any, *names: str) -> Any:
    for name in names:
        if isinstance(row, dict) and name in row:
            return row[name]
        try:
            return row[name]
        except (IndexError, KeyError, TypeError):
            pass
        value = getattr(row, name, None)
        if value is not None:
            return value
    return None
