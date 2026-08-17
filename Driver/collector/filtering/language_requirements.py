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


def language_expression(language: str) -> str:
    return rf"(?<!\w){re.escape(language)}(?!\w)"


def level_expression(level: str) -> str:
    return rf"(?<![A-Za-z0-9]){re.escape(level)}(?:\+)?(?![A-Za-z0-9])"


@lru_cache(maxsize=None)
def explicit_requirement_pattern(
    template: str,
    language: str,
    level: str,
) -> re.Pattern[str]:
    if "{language}" not in template or "{level}" not in template:
        raise ValueError(
            "Language requirement template must contain both "
            f"{{language}} and {{level}}: {template}"
        )
    return re.compile(
        template.replace("{language}", language_expression(language)).replace(
            "{level}", level_expression(level)
        ),
        re.IGNORECASE,
    )


@lru_cache(maxsize=None)
def implied_requirement_pattern(
    template: str,
    language: str,
) -> re.Pattern[str]:
    if "{language}" not in template or "{level}" in template:
        raise ValueError(
            "Implied language requirement template must contain {language} "
            f"and must not contain {{level}}: {template}"
        )
    return re.compile(
        template.replace("{language}", language_expression(language)),
        re.IGNORECASE,
    )


def configured_implied_templates(
    config: configparser.ConfigParser,
    levels: list[str],
) -> list[tuple[str, str]]:
    return [
        (level, template)
        for level in levels
        for template in configured_option_values(
            config,
            "implied_level_templates",
            level,
        )
    ]


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


def extract_language_requirements(
    text: str,
    config_path: Path,
) -> list[LanguageRequirement]:
    config = load_config(config_path)
    if not enabled(config):
        return []

    languages = configured_values(config, "languages")
    levels = configured_values(config, "cefr_levels")
    explicit_templates = configured_values(config, "requirement_templates")
    implied_templates = configured_implied_templates(config, levels)
    optional_patterns = [
        re.compile(pattern, re.IGNORECASE)
        for pattern in configured_values(config, "optional_signals")
    ]

    results: list[LanguageRequirement] = []
    seen: set[tuple[str, str]] = set()
    for unit, context in text_units(text):
        if any(pattern.search(context) for pattern in optional_patterns):
            continue
        for language in languages:
            for level in levels:
                for template in explicit_templates:
                    if not explicit_requirement_pattern(
                        template,
                        language,
                        level,
                    ).search(unit):
                        continue
                    key = (language.casefold(), level.casefold())
                    if key in seen:
                        continue
                    seen.add(key)
                    results.append(
                        LanguageRequirement(
                            name=language,
                            level=level.upper(),
                            level_rank=language_rank(level),
                            original=unit,
                            pattern=template,
                        )
                    )
            for level, template in implied_templates:
                if not implied_requirement_pattern(template, language).search(unit):
                    continue
                key = (language.casefold(), level.casefold())
                if key in seen:
                    continue
                seen.add(key)
                results.append(
                    LanguageRequirement(
                        name=language,
                        level=level.upper(),
                        level_rank=language_rank(level),
                        original=unit,
                        pattern=template,
                    )
                )
    return results
