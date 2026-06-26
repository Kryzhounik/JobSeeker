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


@dataclass(frozen=True)
class FilterResult:
    passed: bool
    fitability_percent: int
    reason: str


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def default_resume_path() -> Path:
    return project_root() / "common" / "config" / "resume.ini"


def default_filter_path() -> Path:
    return project_root() / "common" / "config" / "filter.ini"


def load_filter_config(path: Path | None = None) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path or default_filter_path(), encoding="utf-8")
    return config


def load_resume(path: Path | None = None) -> dict[str, int]:
    config = configparser.ConfigParser()
    config.optionxform = str
    config.read(path or default_resume_path(), encoding="utf-8")

    result: dict[str, int] = {}
    if not config.has_section("languages"):
        return result

    for language, level in config.items("languages"):
        result[language.lower()] = language_rank(level)
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


def normalized(value: object) -> str:
    return str(value or "").strip().lower()


def split_match_values(value: object) -> list[str]:
    return [
        item.strip()
        for item in re.split(r"[,;/|]+|\bor\b|\band\b", normalized(value))
        if item.strip()
    ]


def matches_allowed(value: object, allowed_values: Iterable[str]) -> bool:
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

    allowed = csv_values(setting(config, "remote", "allowed_scopes"))
    if matches_allowed(scope, allowed):
        return FilterResult(True, 100, f"remote filter passed: {scope}")

    return FilterResult(False, 0, f"remote filter failed: {scope}")


def evaluate_relocation(
    relocation: str,
    config: configparser.ConfigParser,
    relocation_mode: str,
) -> FilterResult:
    if relocation_mode == "off":
        return FilterResult(True, 100, "relocation filter disabled")

    destination = str(relocation or "").strip()
    if normalized(destination) in NO_VALUES:
        return FilterResult(False, 0, "relocation filter failed: NO")

    if relocation_mode == "on":
        return FilterResult(True, 100, f"relocation filter passed: {destination}")

    allowed = csv_values(setting(config, "relocation", "allowed_destinations"))
    if matches_allowed(destination, allowed):
        return FilterResult(True, 100, f"relocation filter passed: {destination}")

    return FilterResult(False, 0, f"relocation filter failed: {destination}")


def evaluate_remote_or_relocation(
    remote_type: str,
    remote_scope: str,
    relocation: str,
    config: configparser.ConfigParser,
) -> FilterResult:
    checks: list[FilterResult] = []
    remote_mode = filter_mode(config, "filters", "remote", "off")
    relocation_mode = filter_mode(config, "filters", "relocation", "off")

    if remote_mode != "off":
        checks.append(evaluate_remote(remote_type, remote_scope, config, remote_mode))
    if relocation_mode != "off":
        checks.append(evaluate_relocation(relocation, config, relocation_mode))

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
    remote_type: str = "",
    remote_scope: str = "",
    relocation: str = "",
    resume_path: Path | None = None,
    filter_path: Path | None = None,
) -> FilterResult:
    config = load_filter_config(filter_path)

    if enabled(config, "filters", "title", False):
        blocked_terms = csv_values(setting(config, "title", "blocked_terms"))
        title_result = evaluate_title(title, blocked_terms)
        if not title_result.passed:
            return title_result

    if enabled(config, "filters", "languages", True):
        language_result = evaluate_required_languages(
            required_languages,
            resume_path=resume_path,
        )
        if not language_result.passed:
            return language_result

    logistics_result = evaluate_remote_or_relocation(
        remote_type,
        remote_scope,
        relocation,
        config,
    )
    if not logistics_result.passed:
        return logistics_result

    return FilterResult(True, 100, "job filter passed")


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
