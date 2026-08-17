"""Conservative template-based extraction of human-language requirements."""

from __future__ import annotations

import configparser
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
import re

from analyzer.candidate_fit.filter import language_rank


@dataclass(frozen=True)
class LanguageRequirement:
    name: str
    level: str
    level_rank: int
    original: str
    pattern: str

    def as_filter_row(self) -> dict[str, object]:
        return {
            "name": self.name,
            "level": self.level,
            "level_rank": self.level_rank,
            "raw_value": self.original,
        }


@dataclass(frozen=True)
class CompiledRequirementPattern:
    source: str
    regex: re.Pattern[str]
    implied_level: str = ""


def load_config(path: Path) -> configparser.ConfigParser:
    config = configparser.ConfigParser(interpolation=None)
    config.read(path, encoding="utf-8")
    return config


def configured_values(
    config: configparser.ConfigParser,
    section: str,
) -> list[str]:
    return configured_option_values(config, section, "values")


def configured_option_values(
    config: configparser.ConfigParser,
    section: str,
    option: str,
) -> list[str]:
    value = config.get(section, option, fallback="")
    return [line.strip() for line in value.splitlines() if line.strip()]


def enabled(config: configparser.ConfigParser) -> bool:
    return config.get("filters", "enabled", fallback="on").strip().lower() not in {
        "off",
        "false",
        "no",
        "0",
    }


def named_alternation(
    values: list[str],
    group_name: str,
    *,
    optional_plus: bool = False,
) -> str:
    alternatives = "|".join(
        re.escape(value)
        for value in sorted(values, key=lambda item: (-len(item), item.casefold()))
    )
    if not alternatives:
        return rf"(?P<{group_name}>(?!))"
    suffix = r"(?:\+)?" if optional_plus else ""
    boundary = r"[A-Za-z0-9]" if group_name == "level" else r"\w"
    return (
        rf"(?<!{boundary})(?P<{group_name}>(?:{alternatives}){suffix})"
        rf"(?!{boundary})"
    )


def compile_requirement_pattern(
    template: str,
    language_group: str,
    level_group: str | None,
    *,
    implied_level: str = "",
) -> CompiledRequirementPattern:
    if "{language}" not in template:
        raise ValueError(
            f"Language requirement template must contain {{language}}: {template}"
        )
    if level_group is None and "{level}" in template:
        raise ValueError(
            f"Implied language template must not contain {{level}}: {template}"
        )
    if level_group is not None and "{level}" not in template:
        raise ValueError(
            f"Explicit language template must contain {{level}}: {template}"
        )

    expression = template.replace("{language}", language_group)
    if level_group is not None:
        expression = expression.replace("{level}", level_group)
    return CompiledRequirementPattern(
        source=template,
        regex=re.compile(expression, re.IGNORECASE),
        implied_level=implied_level,
    )


def text_units(text: str) -> list[tuple[str, str]]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    units: list[tuple[str, str]] = []
    for index, line in enumerate(lines):
        context = " ".join(lines[max(0, index - 1) : index + 2])
        units.append((line, context))
    for index in range(len(lines) - 1):
        unit = f"{lines[index]} {lines[index + 1]}"
        context = " ".join(lines[max(0, index - 1) : index + 3])
        units.append((unit, context))
    return units


class LanguageRequirementExtractor:
    def __init__(self, config_path: Path) -> None:
        self.config_path = config_path
        config = load_config(config_path)
        self.enabled = enabled(config)

        languages = configured_values(config, "languages")
        levels = configured_values(config, "cefr_levels")
        self.language_names = {
            language.casefold(): language for language in languages
        }
        self.level_names = {level.casefold(): level.upper() for level in levels}

        language_group = named_alternation(languages, "language")
        level_group = named_alternation(
            levels,
            "level",
            optional_plus=True,
        )
        patterns = [
            compile_requirement_pattern(
                template,
                language_group,
                level_group,
            )
            for template in configured_values(config, "requirement_templates")
        ]
        for level in levels:
            patterns.extend(
                compile_requirement_pattern(
                    template,
                    language_group,
                    None,
                    implied_level=level,
                )
                for template in configured_option_values(
                    config,
                    "implied_level_templates",
                    level,
                )
            )
        self.patterns = tuple(patterns)
        self.optional_patterns = tuple(
            re.compile(pattern, re.IGNORECASE)
            for pattern in configured_values(config, "optional_signals")
        )

    def extract(self, text: str) -> list[LanguageRequirement]:
        if not self.enabled or not self.patterns:
            return []

        results: list[LanguageRequirement] = []
        seen: set[tuple[str, str]] = set()
        for unit, context in text_units(text):
            if any(pattern.search(context) for pattern in self.optional_patterns):
                continue
            for pattern in self.patterns:
                match = pattern.regex.search(unit)
                if match is None:
                    continue

                matched_language = match.group("language")
                language = self.language_names.get(
                    matched_language.casefold(),
                    matched_language,
                )
                matched_level = match.groupdict().get("level")
                level = self.normalize_level(
                    matched_level or pattern.implied_level
                )
                key = (language.casefold(), level.casefold())
                if key in seen:
                    continue
                seen.add(key)
                results.append(
                    LanguageRequirement(
                        name=language,
                        level=level,
                        level_rank=language_rank(level),
                        original=unit,
                        pattern=pattern.source,
                    )
                )
        return results

    def normalize_level(self, value: str) -> str:
        raw = value.strip()
        has_plus = raw.endswith("+")
        base = raw[:-1] if has_plus else raw
        normalized = self.level_names.get(base.casefold(), base.upper())
        return normalized + ("+" if has_plus else "")


@lru_cache(maxsize=32)
def cached_extractor(
    resolved_path: str,
    modified_ns: int,
) -> LanguageRequirementExtractor:
    del modified_ns
    return LanguageRequirementExtractor(Path(resolved_path))


def load_language_requirement_extractor(
    config_path: Path,
) -> LanguageRequirementExtractor:
    resolved = config_path.resolve()
    modified_ns = resolved.stat().st_mtime_ns if resolved.exists() else 0
    return cached_extractor(str(resolved), modified_ns)


def extract_language_requirements(
    text: str,
    config_path: Path,
) -> list[LanguageRequirement]:
    return load_language_requirement_extractor(config_path).extract(text)
