"""Fast candidate-fit filter.

This answers "can this candidate consider this job at all?" using deterministic
rules from config/resume. Deeper resume matching belongs in evaluate.md/Codex.
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import configparser
import json
from pathlib import Path
import re
import sys
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from analyzer.location import check_location_allowance


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
    ".net": ".net",
    ".net core": ".net",
    ".net framework": ".net",
    "asp net": ".net",
    "asp.net": ".net",
    "cpp": "c++",
    "c++": "c++",
    "clojure": "clojure",
    "dart": "dart",
    "elixir": "elixir",
    "erlang": "erlang",
    "f sharp": "f#",
    "f#": "f#",
    "fsharp": "f#",
    "go": "go",
    "golang": "go",
    "groovy": "groovy",
    "haskell": "haskell",
    "java": "java",
    "javascript": "javascript",
    "js": "javascript",
    "kotlin": "kotlin",
    "lua": "lua",
    "matlab": "matlab",
    "dot net": ".net",
    "dotnet": ".net",
    "dotnet core": ".net",
    "node": "javascript",
    "node js": "javascript",
    "node.js": "javascript",
    "objective c": "objective-c",
    "objective-c": "objective-c",
    "objectivec": "objective-c",
    "objc": "objective-c",
    "perl": "perl",
    "php": "php",
    "python": "python",
    "r": "r",
    "ruby": "ruby",
    "rust": "rust",
    "scala": "scala",
    "swift": "swift",
    "typescript": "typescript",
    "ts": "typescript",
    "wordpress": "wordpress",
}
KOTLIN_JVM_FALLBACK = "java"
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
    reason_code: str
    reason: str


def project_root() -> Path:
    return ROOT


def default_resume_path() -> Path:
    return project_root() / "analyzer" / "config" / "resume.ini"


def default_filter_path() -> Path:
    return project_root() / "analyzer" / "candidate_fit" / "config" / "filter.ini"


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


def scored_locations(
    config: configparser.ConfigParser,
    section: str,
    *,
    skip_keys: set[str] | None = None,
) -> list[str]:
    if not config.has_section(section):
        return []
    skipped = {key.lower() for key in (skip_keys or set())}
    return [key for key, _ in config.items(section) if key.lower() not in skipped]


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


def split_alternative_values(value: object) -> list[str]:
    return [
        item.strip()
        for item in re.split(r"[/|]+|\bor\b", normalized(value))
        if item.strip()
    ]


def is_timezone_scope(value: object) -> bool:
    text = normalized(value)
    return any(marker in text for marker in TIMEZONE_MARKERS)


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


def is_mixed_alternative_item(value: object) -> bool:
    parts = split_alternative_values(value)
    if len(parts) < 2:
        return False

    language_count = sum(1 for part in parts if canonical_programming_language(part))
    return 0 < language_count < len(parts)


def technology_requirement_type(row: Any) -> str:
    value = normalized(row_value(row, "requirement", "requirement_type"))
    if value in {"nice_to_have", "nice to have", "optional", "opt"}:
        return "nice_to_have"
    if value in {"core", "required", "important", "desired"}:
        return value
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
        if is_mixed_alternative_item(name):
            continue

        options = programming_language_options(name)
        if not options:
            continue

        if any(programming_language_level(option, levels) >= required_rank for option in options):
            continue

        label = str(name or "").strip()
        return FilterResult(
            False,
            0,
            "tech",
            f"required programming language missing: {label} rank {required_rank}",
        )

    return FilterResult(True, 100, "ok", "programming language filter passed")


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
            return FilterResult(False, 0, "lang", f"English above B2 required: {level}")

        own_rank = resume.get(key)
        if own_rank is None:
            return FilterResult(
                False,
                0,
                "lang",
                f"Required language not in resume: {language}",
            )

        if required_rank and own_rank < required_rank:
            return FilterResult(
                False,
                0,
                "lang",
                f"{language} required {level}, resume lower",
            )

    return FilterResult(True, 100, "ok", "language filter passed")


def evaluate_remote(
    remote_type: str,
    remote_scope: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
    remote_mode: str,
) -> FilterResult:
    if remote_mode == "off":
        return FilterResult(True, 100, "ok", "remote filter disabled")

    if normalized(remote_type) != "remote":
        return FilterResult(
            False,
            0,
            "loc",
            f"remote filter failed: {remote_type or 'empty'}",
        )

    scope = str(remote_scope or "").strip()
    if remote_mode == "on":
        return FilterResult(True, 100, "ok", f"remote filter passed: {scope or 'remote'}")

    if normalized(scope) == "unknown":
        return FilterResult(True, 100, "ok", "remote scope is unknown; hard reject skipped")

    if normalized(scope) in NO_VALUES:
        return FilterResult(False, 0, "loc", "remote filter failed: remote scope is empty")

    if is_timezone_scope(scope):
        return FilterResult(True, 100, "ok", f"remote timezone scope accepted: {scope}")

    allowed = scored_locations(resume_config, "locations")
    if check_location_allowance(scope, allowed):
        return FilterResult(True, 100, "ok", f"remote filter passed: {scope}")

    return FilterResult(False, 0, "loc", f"remote filter failed: {scope}")


def evaluate_relocation(
    relocation: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
    relocation_mode: str,
) -> FilterResult:
    if relocation_mode == "off":
        return FilterResult(True, 100, "ok", "relocation filter disabled")

    destination = str(relocation or "").strip()
    if normalized(destination) in NO_VALUES:
        return FilterResult(False, 0, "loc", "relocation filter failed: NO")

    if relocation_mode == "on":
        return FilterResult(True, 100, "ok", f"relocation filter passed: {destination}")

    allowed = scored_locations(resume_config, "relocation", skip_keys={"base"})
    if check_location_allowance(destination, allowed):
        return FilterResult(True, 100, "ok", f"relocation filter passed: {destination}")

    return FilterResult(False, 0, "loc", f"relocation filter failed: {destination}")


def evaluate_location_filters(
    location: str,
    remote_type: str,
    remote_scope: str,
    relocation: str,
    config: configparser.ConfigParser,
    resume_config: configparser.ConfigParser,
) -> FilterResult:
    checks: list[FilterResult] = []
    work_type = normalized(remote_type)
    if work_type in {"hybrid", "office"}:
        allowed = scored_locations(resume_config, "locations")
        if check_location_allowance(location, allowed):
            return FilterResult(
                True,
                100,
                "ok",
                f"{work_type} location filter passed: {location}",
            )

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
        return FilterResult(True, 100, "ok", "location filters disabled")

    mode = setting(config, "filters", "remote_relocation_mode", "any").lower()
    if mode == "all":
        if all(result.passed for result in checks):
            return FilterResult(True, 100, "ok", "remote/relocation filters passed")
        return FilterResult(
            False,
            0,
            "loc",
            "; ".join(result.reason for result in checks if not result.passed),
        )

    for result in checks:
        if result.passed:
            return result

    return FilterResult(False, 0, "loc", "; ".join(result.reason for result in checks))


def evaluate_job(
    *,
    title: str = "",
    required_languages: Iterable[Any] = (),
    technologies: Iterable[Any] = (),
    location: str = "",
    remote_type: str = "",
    remote_scope: str = "",
    relocation: str = "",
    resume_path: Path | None = None,
    filter_path: Path | None = None,
) -> FilterResult:
    config = load_filter_config(filter_path)
    resume_config = load_resume_config(resume_path)

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

    logistics_result = evaluate_location_filters(
        location,
        remote_type,
        remote_scope,
        relocation,
        config,
        resume_config,
    )
    if not logistics_result.passed:
        return logistics_result

    return FilterResult(True, 100, "ok", "job filter passed")


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
        location=str(record.get("location") or ""),
        remote_type=str(record.get("remote_type") or ""),
        remote_scope=str(record.get("remote_scope") or ""),
        relocation=str(record.get("relocation") or ""),
        resume_path=resume_path,
        filter_path=filter_path,
    )


def filter_json_file(input_path: Path, output_path: Path) -> FilterResult:
    record = json.loads(input_path.read_text(encoding="utf-8"))
    if not isinstance(record, dict):
        raise ValueError(f"{input_path} does not contain a JSON object")

    result = filter_job_json(record)
    if not result.passed:
        record.update(
            candidate_fit_percent=result.candidate_fit_percent,
            candidate_fit_reason_code=result.reason_code,
            candidate_fit_reason=result.reason,
        )
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(
            json.dumps(record, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description="Run the fast candidate-fit filter.")
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()

    result = filter_json_file(Path(args.input), Path(args.output))
    print(json.dumps({
        "passed": result.passed,
        "candidate_fit_percent": result.candidate_fit_percent,
        "candidate_fit_reason_code": result.reason_code,
        "candidate_fit_reason": result.reason,
    }, ensure_ascii=False))


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


if __name__ == "__main__":
    main()
