"""Conservative template-based extraction of human-language requirements."""

from __future__ import annotations

import configparser
from dataclasses import dataclass
import json
from pathlib import Path
import re
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[2]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.candidate_fit.filter import language_rank
from collector.filtering.rule_loader import load_rule_module


DEFAULT_LANGUAGE_RULES = Path(__file__).with_name("language_rules.py")
REQUIRED_RULE_NAMES = (
    "LANGUAGE_ALIASES",
    "CEFR_LEVELS",
    "EXPLICIT_REQUIREMENT_TEMPLATES",
    "EXPLICIT_LANGUAGE_LIST_TEMPLATES",
    "IMPLIED_LEVEL_TEMPLATES",
    "IMPLIED_LEVEL_LANGUAGE_LIST_TEMPLATES",
    "REQUIRED_WITHOUT_LEVEL_TEMPLATES",
    "REQUIRED_WITHOUT_LEVEL_LANGUAGE_LIST_TEMPLATES",
    "OPTIONAL_SIGNALS",
)


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
    priority: int = 0


@dataclass(frozen=True)
class CompiledLanguageListPattern:
    source: str
    regex: re.Pattern[str]
    implied_level: str = ""
    priority: int = 0


def load_config(path: Path) -> configparser.ConfigParser:
    config = configparser.ConfigParser(interpolation=None)
    config.optionxform = str
    config.read(path, encoding="utf-8")
    return config


def configured_level(
    config: configparser.ConfigParser,
    policy_name: str,
    valid_levels: tuple[str, ...],
) -> str:
    level = config.get("implied_levels", policy_name, fallback="").strip().upper()
    if level not in valid_levels:
        raise ValueError(
            f"Invalid or missing implied level for {policy_name}: {level!r}"
        )
    return level


def configured_level_aliases(
    config: configparser.ConfigParser,
    valid_levels: tuple[str, ...],
) -> dict[str, str]:
    aliases = {level.casefold(): level for level in valid_levels}
    for alias, raw_level in config.items("level_aliases"):
        level = raw_level.strip().upper()
        if level not in valid_levels:
            raise ValueError(f"Invalid CEFR level for {alias}: {raw_level!r}")
        aliases[alias.strip().casefold()] = level
    return aliases


def alias_index(vocabulary: dict[str, tuple[str, ...]]) -> dict[str, str]:
    return {
        term.casefold(): canonical
        for canonical, aliases in vocabulary.items()
        for term in (canonical, *aliases)
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


def plain_alternation(values: list[str]) -> str:
    alternatives = "|".join(
        re.escape(value)
        for value in sorted(values, key=lambda item: (-len(item), item.casefold()))
    )
    return rf"(?<!\w)(?:{alternatives})(?!\w)" if alternatives else r"(?!)"


def compile_requirement_pattern(
    template: str,
    language_group: str,
    level_group: str | None,
    alternative_level_group: str | None = None,
    *,
    implied_level: str = "",
    priority: int = 0,
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
    if "{alternative_level}" in template and alternative_level_group is None:
        raise ValueError(
            "Language range template requires an alternative level group: "
            + template
        )

    expression = template.replace("{language}", language_group)
    if level_group is not None:
        expression = expression.replace("{level}", level_group)
    if alternative_level_group is not None:
        expression = expression.replace(
            "{alternative_level}",
            alternative_level_group,
        )
    return CompiledRequirementPattern(
        source=template,
        regex=re.compile(expression, re.IGNORECASE),
        implied_level=implied_level,
        priority=priority,
    )


def compile_language_list_pattern(
    template: str,
    language_list_group: str,
    level_group: str | None,
    alternative_level_group: str | None = None,
    *,
    implied_level: str = "",
    priority: int = 0,
) -> CompiledLanguageListPattern:
    if "{languages}" not in template:
        raise ValueError(
            "Language-list requirement template must contain {languages}: "
            + template
        )
    if level_group is None and "{level}" in template:
        raise ValueError(
            "Implied language-list template must not contain {level}: " + template
        )
    if level_group is not None and "{level}" not in template:
        raise ValueError(
            "Explicit language-list template must contain {level}: " + template
        )

    expression = template.replace("{languages}", language_list_group)
    if level_group is not None:
        expression = expression.replace("{level}", level_group)
    if alternative_level_group is not None:
        expression = expression.replace(
            "{alternative_level}",
            alternative_level_group,
        )
    return CompiledLanguageListPattern(
        source=template,
        regex=re.compile(expression, re.IGNORECASE),
        implied_level=implied_level,
        priority=priority,
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
    def __init__(
        self,
        config_path: Path,
        rules_path: Path = DEFAULT_LANGUAGE_RULES,
    ) -> None:
        self.config_path = config_path
        self.rules_path = rules_path
        config = load_config(config_path)
        rules = load_rule_module(rules_path, REQUIRED_RULE_NAMES)

        levels = tuple(rules["CEFR_LEVELS"])
        language_aliases = dict(rules["LANGUAGE_ALIASES"])
        self.language_names = alias_index(language_aliases)
        language_terms = list(self.language_names)
        self.level_names = configured_level_aliases(config, levels)
        level_terms = list(self.level_names)

        language_group = named_alternation(language_terms, "language")
        self.language_presence_pattern = re.compile(language_group, re.IGNORECASE)
        language_atom = plain_alternation(language_terms)
        conjunction = r"(?:and|та|і|и|i|oraz|și|და|және)"
        list_separator = rf"\s*(?:,\s*(?:{conjunction}\s+)?|{conjunction}\s+|&\s*)"
        language_list_group = (
            rf"(?P<languages>{language_atom}(?:{list_separator}{language_atom})+)"
        )
        self.alternative_language_list_pattern = re.compile(
            rf"{language_atom}\s*(?:and/or|or|/|або|или|lub|sau|ან|немесе)\s*{language_atom}",
            re.IGNORECASE,
        )
        level_group = named_alternation(
            level_terms,
            "level",
            optional_plus=True,
        )
        alternative_level_group = named_alternation(
            level_terms,
            "alternative_level",
            optional_plus=True,
        )
        patterns = [
            compile_requirement_pattern(
                template,
                language_group,
                level_group,
                alternative_level_group,
                priority=30,
            )
            for template in rules["EXPLICIT_REQUIREMENT_TEMPLATES"]
        ]
        implied_templates = dict(rules["IMPLIED_LEVEL_TEMPLATES"])
        for policy_name, templates in implied_templates.items():
            level = configured_level(config, policy_name, levels)
            patterns.extend(
                compile_requirement_pattern(
                    template,
                    language_group,
                    None,
                    implied_level=level,
                    priority=20,
                )
                for template in templates
            )
        required_level = configured_level(config, "required", levels)
        patterns.extend(
            compile_requirement_pattern(
                template,
                language_group,
                None,
                implied_level=required_level,
                priority=10,
            )
            for template in rules["REQUIRED_WITHOUT_LEVEL_TEMPLATES"]
        )
        self.patterns = tuple(patterns)
        list_patterns = [
            compile_language_list_pattern(
                template,
                language_list_group,
                level_group,
                alternative_level_group,
                priority=30,
            )
            for template in rules["EXPLICIT_LANGUAGE_LIST_TEMPLATES"]
        ]
        implied_list_templates = dict(
            rules["IMPLIED_LEVEL_LANGUAGE_LIST_TEMPLATES"]
        )
        for policy_name, templates in implied_list_templates.items():
            level = configured_level(config, policy_name, levels)
            list_patterns.extend(
                compile_language_list_pattern(
                    template,
                    language_list_group,
                    None,
                    implied_level=level,
                    priority=20,
                )
                for template in templates
            )
        list_patterns.extend(
            compile_language_list_pattern(
                template,
                language_list_group,
                None,
                implied_level=required_level,
                priority=10,
            )
            for template in rules[
                "REQUIRED_WITHOUT_LEVEL_LANGUAGE_LIST_TEMPLATES"
            ]
        )
        self.list_patterns = tuple(list_patterns)
        self.optional_patterns = tuple(
            re.compile(pattern, re.IGNORECASE)
            for pattern in rules["OPTIONAL_SIGNALS"]
        )

    def extract(self, text: str) -> list[LanguageRequirement]:
        if not self.patterns:
            return []
        if self.language_presence_pattern.search(text) is None:
            return []

        results: dict[str, tuple[LanguageRequirement, int]] = {}
        for unit, context in text_units(text):
            if self.language_presence_pattern.search(unit) is None:
                continue
            if self.alternative_language_list_pattern.search(unit) is not None:
                continue
            if any(pattern.search(context) for pattern in self.optional_patterns):
                continue
            for pattern in self.patterns:
                for match in pattern.regex.finditer(unit):
                    matched_language = match.group("language")
                    language = self.language_names.get(
                        matched_language.casefold(),
                        matched_language,
                    )
                    matched_level = match.groupdict().get("level")
                    alternative_level = match.groupdict().get("alternative_level")
                    levels = [
                        self.normalize_level(value)
                        for value in (matched_level, alternative_level)
                        if value
                    ]
                    if levels:
                        level = min(levels, key=language_rank)
                    else:
                        level = self.normalize_level(pattern.implied_level)
                    self.record_requirement(
                        results,
                        LanguageRequirement(
                            name=language,
                            level=level,
                            level_rank=language_rank(level),
                            original=unit,
                            pattern=pattern.source,
                        ),
                        pattern.priority,
                    )
            for pattern in self.list_patterns:
                match = pattern.regex.search(unit)
                if match is None:
                    continue
                matched_level = match.groupdict().get("level")
                alternative_level = match.groupdict().get("alternative_level")
                matched_levels = [
                    self.normalize_level(value)
                    for value in (matched_level, alternative_level)
                    if value
                ]
                if matched_levels:
                    level = min(matched_levels, key=language_rank)
                else:
                    level = self.normalize_level(pattern.implied_level)
                language_list = match.group("languages")
                for language_match in self.language_presence_pattern.finditer(
                    language_list
                ):
                    matched_language = language_match.group("language")
                    language = self.language_names.get(
                        matched_language.casefold(),
                        matched_language,
                    )
                    self.record_requirement(
                        results,
                        LanguageRequirement(
                            name=language,
                            level=level,
                            level_rank=language_rank(level),
                            original=unit,
                            pattern=pattern.source,
                        ),
                        pattern.priority,
                    )
        return [requirement for requirement, _priority in results.values()]

    @staticmethod
    def record_requirement(
        results: dict[str, tuple[LanguageRequirement, int]],
        requirement: LanguageRequirement,
        priority: int,
    ) -> None:
        key = requirement.name.casefold()
        existing = results.get(key)
        if existing is not None:
            existing_requirement, existing_priority = existing
            if existing_priority > priority:
                return
            if (
                existing_priority == priority
                and existing_requirement.level_rank >= requirement.level_rank
            ):
                return
        results[key] = (requirement, priority)

    def normalize_level(self, value: str) -> str:
        raw = value.strip()
        has_plus = raw.endswith("+")
        base = raw[:-1] if has_plus else raw
        normalized = self.level_names.get(base.casefold(), base.upper())
        return normalized + ("+" if has_plus else "")


def load_language_requirement_extractor(
    config_path: Path,
    rules_path: Path = DEFAULT_LANGUAGE_RULES,
) -> LanguageRequirementExtractor:
    return LanguageRequirementExtractor(config_path, rules_path)


def extract_language_requirements(
    text: str,
    config_path: Path,
    rules_path: Path = DEFAULT_LANGUAGE_RULES,
) -> list[LanguageRequirement]:
    return load_language_requirement_extractor(config_path, rules_path).extract(text)


if __name__ == "__main__":
    config_path = Path(__file__).with_name("linkedin_language_filter.ini")
    requirements = extract_language_requirements(
        sys.stdin.buffer.read().decode("utf-8"),
        config_path,
    )
    print(json.dumps([item.as_filter_row() for item in requirements], ensure_ascii=False))
