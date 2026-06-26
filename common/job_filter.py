from __future__ import annotations

from dataclasses import dataclass
import configparser
from pathlib import Path
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


@dataclass(frozen=True)
class FilterResult:
    passed: bool
    fitability_percent: int
    reason: str


def project_root() -> Path:
    return Path(__file__).resolve().parents[1]


def default_resume_path() -> Path:
    return project_root() / "common" / "config" / "resume.ini"


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


def evaluate_job(
    *,
    title: str = "",
    required_languages: Iterable[Any] = (),
    resume_path: Path | None = None,
) -> FilterResult:
    title_result = evaluate_title(title)
    if not title_result.passed:
        return title_result
    return evaluate_required_languages(required_languages, resume_path=resume_path)


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
