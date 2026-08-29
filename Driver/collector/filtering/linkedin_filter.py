from __future__ import annotations

import argparse
import configparser
from contextlib import closing
from dataclasses import dataclass
from functools import lru_cache
import json
from pathlib import Path
import re
import sqlite3
import sys
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))
LOGGING_ROOT = ROOT / "collector" / "logging"
if str(LOGGING_ROOT) not in sys.path:
    sys.path.insert(0, str(LOGGING_ROOT))

from analyzer.candidate_fit.filter import default_resume_path
from analyzer.candidate_fit.filter import evaluate_required_languages
from analyzer.candidate_fit.filter import load_resume
from collector.filtering.language_requirements import (
    load_language_requirement_extractor,
)
from db.companies import normalize_company_name
from db.config import COMPANY_FILTER
from db.config import FILTER_DEFAULTS
from db.config import LANGUAGE_FILTER
from db.config import TECHNOLOGY_FILTER
from db.config import TITLE_FILTER
from db.config import load_filter_switches
from db.job_registry import is_registered
from db.filter_rejections import save_content_filter_rejection
from db.migrate import migrate_database
from db.readable_text import load_readable_text
from linkedin_logger import record_preview_filter


DEFAULT_PREVIEW_CONFIG = Path(__file__).with_name("linkedin_preview_filter.ini")
DEFAULT_CONTENT_CONFIG = Path(__file__).with_name("linkedin_content_filter.ini")
DEFAULT_LANGUAGE_CONFIG = Path(__file__).with_name("linkedin_language_filter.ini")
DEFAULT_RESUME_CONFIG = default_resume_path()
DEFAULT_DB = ROOT.parent / "Data" / "jobs.sqlite"
MIGRATED_DATABASES: set[Path] = set()


@dataclass(frozen=True)
class Vacancy:
    title: str
    text: str | None = None
    company: str = ""


@dataclass(frozen=True)
class FilterResult:
    rejected: bool
    reason: str
    rule: str = ""
    match: str = ""
    terms: tuple[str, ...] = ()
    technologies: tuple[str, ...] = ()
    languages: tuple[str, ...] = ()
    signals: tuple[str, ...] = ()
    keyword_patterns: tuple[tuple[str, str], ...] = ()


def load_config(path: Path) -> configparser.ConfigParser:
    config = configparser.ConfigParser(interpolation=None)
    config.read(path, encoding="utf-8")
    return config


def enabled(config: configparser.ConfigParser) -> bool:
    return config.get("filters", "enabled", fallback="on").strip().lower() not in {
        "off",
        "false",
        "no",
        "0",
    }


def configured_values(config: configparser.ConfigParser, section: str) -> list[str]:
    value = config.get(section, "values", fallback="")
    return [line.strip() for line in value.splitlines() if line.strip()]


def configured_names(config: configparser.ConfigParser, section: str) -> list[str]:
    value = config.get(section, "values", fallback="")
    return [name.strip() for name in re.split(r"[,\n]+", value) if name.strip()]


def configured_patterns(
    config: configparser.ConfigParser,
    section: str,
) -> list[re.Pattern[str]]:
    return [
        re.compile(pattern, re.IGNORECASE)
        for pattern in configured_values(config, section)
    ]


def blocked_terms(
    config: configparser.ConfigParser,
    config_path: Path = DEFAULT_PREVIEW_CONFIG,
) -> list[str]:
    terms = [
        item.strip()
        for item in config.get("title", "blocked_terms", fallback="").split(",")
        if item.strip()
    ]
    terms_file = config.get("title", "blocked_terms_file", fallback="").strip()
    if not terms_file:
        return terms

    path = Path(terms_file)
    if not path.is_absolute():
        path = config_path.parent / path
    if path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            term = line.strip()
            if term and not term.startswith("#"):
                terms.append(term)
    return terms


def matches_term(text: str, term: str) -> bool:
    haystack = text.lower()
    needle = term.lower().strip()
    if not needle:
        return False
    if any(not char.isalnum() for char in needle):
        return needle in haystack
    return re.search(
        rf"(?<![a-z0-9]){re.escape(needle)}(?![a-z0-9])",
        haystack,
    ) is not None


YEARS_COUNT_EXPRESSION = (
    r"(?:[2-9]|[1-9]\d+|two|three|four|five|six|seven|eight|nine|ten)"
)
YEARS_UNIT_EXPRESSION = r"(?:years?|роки|років)"
YEARS_EXPRESSION = (
    rf"{YEARS_COUNT_EXPRESSION}"
    rf"(?:\+|\s*(?:[-\u2013\u2014]|to)\s*{YEARS_COUNT_EXPRESSION})?"
    rf"\s+{YEARS_UNIT_EXPRESSION}"
)
TECHNOLOGY_LIST_GROUP = "technologies"
TECHNOLOGY_LIST_EXPRESSION = (
    rf"(?P<{TECHNOLOGY_LIST_GROUP}>[^;:\r\n]{{1,200}}?)"
    r"(?=\.(?:\s|$)|[;:\r\n]|$)"
)
TECHNOLOGY_LIST_SEPARATOR = re.compile(
    r"\s*(?:\band/or\b|\bor\b|\band\b|\bабо\b|\bчи\b|\bта\b|\bі\b|,|/)\s*",
    re.IGNORECASE,
)
TECHNOLOGY_LIST_OR = re.compile(
    r"\band/or\b|\bor\b|\bабо\b|\bчи\b",
    re.IGNORECASE,
)


def technology_expression(name: str) -> str:
    return rf"(?<![\w+#]){re.escape(name)}(?![\w+#])"


def technology_pattern(name: str) -> re.Pattern[str]:
    flags = 0 if name == "Go" else re.IGNORECASE
    return re.compile(technology_expression(name), flags)


def technology_matches(
    text: str,
    patterns: tuple[tuple[str, re.Pattern[str]], ...],
) -> list[str]:
    candidates = []
    for name, pattern in patterns:
        match = pattern.search(text)
        if match is not None:
            candidates.append((name, match.start(), match.end()))

    selected = []
    for name, start, end in candidates:
        if any(
            other_start <= start
            and end <= other_end
            and other_end - other_start > end - start
            for _, other_start, other_end in candidates
        ):
            continue
        selected.append(name)
    return selected


@lru_cache(maxsize=None)
def requirement_pattern(template: str, technology: str) -> re.Pattern[str]:
    if "{technology}" not in template:
        raise ValueError(
            "Hard requirement template must contain {technology}: " + template
        )
    expression = technology_expression(technology)
    if technology == "Go":
        expression = f"(?-i:{expression})"
    return re.compile(
        template.replace("{technology}", expression).replace(
            "{years}", YEARS_EXPRESSION
        ),
        re.IGNORECASE,
    )


@lru_cache(maxsize=None)
def technology_list_pattern(template: str) -> re.Pattern[str]:
    if "{technologies}" not in template:
        raise ValueError(
            "Technology list template must contain {technologies}: " + template
        )
    return re.compile(
        template.replace("{technologies}", TECHNOLOGY_LIST_EXPRESSION).replace(
            "{years}",
            YEARS_EXPRESSION,
        ),
        re.IGNORECASE,
    )


def split_technology_list(value: str) -> list[str]:
    return [
        item.strip()
        for item in TECHNOLOGY_LIST_SEPARATOR.split(value)
        if item.strip()
    ]


def content_units(text: str) -> list[tuple[str, str]]:
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    units = []
    for index, line in enumerate(lines):
        context = " ".join(lines[max(0, index - 1) : index + 2])
        units.append((line, context))
    for index in range(len(lines) - 1):
        unit = f"{lines[index]} {lines[index + 1]}"
        context = " ".join(lines[max(0, index - 1) : index + 3])
        units.append((unit, context))
    return units


class VacancyFilter:
    def __init__(
        self,
        preview_config_path: Path = DEFAULT_PREVIEW_CONFIG,
        content_config_path: Path = DEFAULT_CONTENT_CONFIG,
        language_config_path: Path = DEFAULT_LANGUAGE_CONFIG,
        resume_path: Path = DEFAULT_RESUME_CONFIG,
        db_path: Path = DEFAULT_DB,
    ) -> None:
        self.preview_config_path = preview_config_path
        self.content_config_path = content_config_path
        self.language_config_path = language_config_path
        self.resume_path = resume_path
        self.db_path = db_path
        self.preview_config = load_config(preview_config_path)
        self.content_config = load_config(content_config_path)
        self.title_blocked_terms = tuple(
            blocked_terms(self.preview_config, self.preview_config_path)
        )
        self.pass_word_patterns = tuple(
            (name, technology_pattern(name))
            for name in configured_names(self.content_config, "pass_words")
        )
        self.blocked_technology_patterns = tuple(
            (name, technology_pattern(name))
            for name in configured_names(
                self.content_config,
                "blocked_technologies",
            )
        )
        self.hard_requirement_templates = tuple(
            configured_values(self.content_config, "hard_requirement_templates")
        )
        self.alternative_technology_list_templates = tuple(
            (template, technology_list_pattern(template))
            for template in configured_values(
                self.content_config,
                "alternative_technology_list_templates",
            )
        )
        self.technology_list_templates = tuple(
            (template, technology_list_pattern(template))
            for template in configured_values(
                self.content_config,
                "technology_list_templates",
            )
        )
        self.content_optional_signals = tuple(
            configured_patterns(self.content_config, "optional_signals")
        )
        self.language_extractor = load_language_requirement_extractor(
            language_config_path
        )
        self.resume_languages = load_resume(resume_path)
        self.filter_switches = {name: True for name in FILTER_DEFAULTS}
        self.blacklisted_companies: frozenset[str] = frozenset()
        self.load_database_snapshot()

    def filter(self, vacancy: Vacancy) -> FilterResult:
        result = self.filter_preview(vacancy.title, vacancy.company)
        if result.rejected or vacancy.text is None:
            return result
        return self.filter_text(vacancy.title, vacancy.text)

    def filter_preview(self, title: str, company: str = "") -> FilterResult:
        result = self.filter_title(title)
        if result.rejected:
            return result
        return self.filter_company(company)

    def filter_title(self, title: str) -> FilterResult:
        if not self.database_filter_enabled(TITLE_FILTER):
            return FilterResult(False, "title filter disabled")
        config = self.preview_config
        if not enabled(config):
            return FilterResult(False, "title filter disabled")

        matches = [
            term
            for term in self.title_blocked_terms
            if matches_term(title, term)
        ]
        if matches:
            return FilterResult(
                True,
                "title blocked: " + ", ".join(matches),
                rule="title_blocked",
                match=title,
                terms=tuple(matches),
            )
        return FilterResult(False, "no title skip signals")

    def filter_company(self, company: str) -> FilterResult:
        if not self.database_filter_enabled(COMPANY_FILTER):
            return FilterResult(False, "company filter disabled")
        name = company.strip()
        if not name:
            return FilterResult(False, "no company skip signals")

        normalized = normalize_company_name(name).casefold()
        if normalized in self.blacklisted_companies:
            return FilterResult(
                True,
                f"company blacklisted: {name}",
                rule="company_blacklisted",
                match=name,
                terms=(name,),
            )
        return FilterResult(False, "no company skip signals")

    def filter_text(self, title: str, text: str) -> FilterResult:
        technology_filter_enabled = self.database_filter_enabled(TECHNOLOGY_FILTER)
        config = self.content_config
        pass_words = [
            name
            for name, pattern in self.pass_word_patterns
            if pattern.search(title)
        ]
        pass_result = None
        if pass_words:
            pass_result = FilterResult(
                False,
                "title pass word: " + ", ".join(pass_words),
                rule="title_pass_word",
                match=title,
            )

        if not pass_result and technology_filter_enabled and enabled(config):
            for unit, context in content_units(text):
                list_handled, list_result = self.filter_technology_lists(
                    unit,
                    context,
                )
                if list_result is not None:
                    return list_result
                if list_handled:
                    continue

                unit_technologies = technology_matches(
                    unit,
                    self.blocked_technology_patterns,
                )
                matches = [
                    (name, template)
                    for name in unit_technologies
                    for template in self.hard_requirement_templates
                    if requirement_pattern(template, name).search(unit)
                ]
                matched_technologies = list(dict.fromkeys(name for name, _ in matches))
                matched_signals = list(dict.fromkeys(template for _, template in matches))
                if not matched_technologies or not matched_signals:
                    continue
                if any(
                    pattern.search(context)
                    for _, pattern in self.pass_word_patterns
                ):
                    continue
                if any(
                    pattern.search(context)
                    for pattern in self.content_optional_signals
                ):
                    continue
                return FilterResult(
                    True,
                    "hard requirement for blocked technology: "
                    + ", ".join(matched_technologies),
                    rule="hard_blocked_technology",
                    match=unit,
                    technologies=tuple(matched_technologies),
                    signals=tuple(matched_signals),
                    keyword_patterns=tuple(dict.fromkeys(matches)),
                )

        language_result = self.filter_language_requirements(text)
        if language_result.rejected:
            return language_result
        if pass_result:
            return pass_result
        if not technology_filter_enabled or not enabled(config):
            return FilterResult(False, "technology content filter disabled")
        return FilterResult(False, "no content skip signals")

    def filter_technology_lists(
        self,
        unit: str,
        context: str,
    ) -> tuple[bool, FilterResult | None]:
        template_groups = (
            (True, self.alternative_technology_list_templates),
            (False, self.technology_list_templates),
        )
        for always_alternative, templates in template_groups:
            for template, pattern in templates:
                match = pattern.search(unit)
                if match is None:
                    continue
                if any(
                    optional.search(context)
                    for optional in self.content_optional_signals
                ):
                    return True, None

                list_text = match.group(TECHNOLOGY_LIST_GROUP)
                items = split_technology_list(list_text)
                if not items:
                    return True, None

                matches_by_item = [
                    technology_matches(item, self.blocked_technology_patterns)
                    for item in items
                ]
                alternative = always_alternative or bool(
                    TECHNOLOGY_LIST_OR.search(list_text)
                )
                if alternative and any(not names for names in matches_by_item):
                    return True, None

                matched_technologies = list(
                    dict.fromkeys(
                        name
                        for names in matches_by_item
                        for name in names
                    )
                )
                if not matched_technologies:
                    return True, None

                return True, FilterResult(
                    True,
                    "hard requirement for blocked technology: "
                    + ", ".join(matched_technologies),
                    rule="hard_blocked_technology",
                    match=unit,
                    technologies=tuple(matched_technologies),
                    signals=(template,),
                    keyword_patterns=tuple(
                        (name, template) for name in matched_technologies
                    ),
                )
        return False, None

    def filter_language_requirements(self, text: str) -> FilterResult:
        if not self.database_filter_enabled(LANGUAGE_FILTER):
            return FilterResult(False, "language filter disabled")
        requirements = self.language_extractor.extract(text)
        if not requirements:
            return FilterResult(False, "no language skip signals")
        for requirement in requirements:
            comparison = evaluate_required_languages(
                [requirement.as_filter_row()],
                resume_languages=self.resume_languages,
            )
            if comparison.passed:
                continue
            label = f"{requirement.name} {requirement.level}"
            return FilterResult(
                True,
                comparison.reason,
                rule="hard_language_requirement",
                match=requirement.original,
                languages=(label,),
                signals=(requirement.pattern,),
                keyword_patterns=((label, requirement.pattern),),
            )
        return FilterResult(False, "no language skip signals")

    def database_filter_enabled(self, config_name: str) -> bool:
        return self.filter_switches.get(config_name, True)

    def load_database_snapshot(self) -> None:
        if not self.db_path.exists():
            return
        resolved_db = ensure_migrated_database(self.db_path)
        with closing(sqlite3.connect(resolved_db)) as connection:
            self.filter_switches = load_filter_switches(connection)
            self.blacklisted_companies = frozenset(
                normalize_company_name(row[0]).casefold()
                for row in connection.execute(
                    "SELECT name FROM companies WHERE blacklisted = 1"
                ).fetchall()
            )


def filter_vacancy(vacancy: Vacancy) -> FilterResult:
    return VacancyFilter().filter(vacancy)


def source_job_id(preview: dict[str, Any]) -> str:
    value = str(preview.get("job_id") or "").strip()
    if value:
        return value
    source_url = str(preview.get("source_url") or "").strip()
    match = re.search(r"/jobs/view/(\d+)", source_url)
    return match.group(1) if match else ""


def ensure_migrated_database(db_path: Path) -> Path:
    resolved_db = db_path.resolve()
    if resolved_db not in MIGRATED_DATABASES:
        migrate_database(resolved_db)
        MIGRATED_DATABASES.add(resolved_db)
    return resolved_db


def is_duplicate_preview(preview: dict[str, Any], db_path: Path = DEFAULT_DB) -> bool:
    if not db_path.exists():
        return False
    job_id = source_job_id(preview)
    if not job_id:
        return False

    resolved_db = ensure_migrated_database(db_path)
    with closing(sqlite3.connect(resolved_db)) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        return is_registered(connection, "linkedin", job_id)


def decide_preview(
    preview: dict[str, Any],
    config_path: Path = DEFAULT_PREVIEW_CONFIG,
    db_path: Path = DEFAULT_DB,
) -> dict[str, Any]:
    config = load_config(config_path)
    vacancy_filter = VacancyFilter(
        preview_config_path=config_path,
        db_path=db_path,
    )
    return decide_preview_with_filter(
        preview,
        config,
        vacancy_filter,
        db_path,
    )


def decide_preview_with_filter(
    preview: dict[str, Any],
    config: configparser.ConfigParser,
    vacancy_filter: VacancyFilter,
    db_path: Path,
) -> dict[str, Any]:
    result = vacancy_filter.filter_preview(
        str(preview.get("title") or "").strip(),
        str(preview.get("company") or "").strip(),
    )
    if result.rejected:
        return {
            "preview_decision": "skip",
            "preview_reason": result.reason,
            "preview_rule": result.rule,
            "preview_blocked_terms": list(result.terms),
        }
    if is_duplicate_preview(preview, db_path):
        return {
            "preview_decision": "skip",
            "preview_reason": "duplicate source_job_id",
            "preview_rule": "duplicate_source_job_id",
            "preview_blocked_terms": [],
        }

    when_unsure = config.get("decision", "when_unsure", fallback="open").strip().lower()
    return {
        "preview_decision": "open" if when_unsure != "skip" else "skip",
        "preview_reason": "no preview skip signals",
        "preview_rule": "",
        "preview_blocked_terms": [],
    }


def decide_content(
    text: str,
    config_path: Path = DEFAULT_CONTENT_CONFIG,
    *,
    title: str = "",
    language_config_path: Path = DEFAULT_LANGUAGE_CONFIG,
    resume_path: Path = DEFAULT_RESUME_CONFIG,
    db_path: Path = DEFAULT_DB,
) -> dict[str, Any]:
    result = VacancyFilter(
        content_config_path=config_path,
        language_config_path=language_config_path,
        resume_path=resume_path,
        db_path=db_path,
    ).filter_text(title, text)
    return content_response(result)


def content_response(result: FilterResult) -> dict[str, Any]:
    response: dict[str, Any] = {
        "content_decision": "skip" if result.rejected else "analyze",
        "content_reason": result.reason,
        "content_rule": result.rule,
        "content_match": result.match,
    }
    if result.technologies:
        response["content_technologies"] = list(result.technologies)
    if result.languages:
        response["content_languages"] = list(result.languages)
    if result.signals:
        response["content_signals"] = list(result.signals)
    return response


def apply_preview_decision(
    payload: Any,
    config_path: Path,
    db_path: Path = DEFAULT_DB,
) -> Any:
    config = load_config(config_path)
    vacancy_filter = VacancyFilter(
        preview_config_path=config_path,
        db_path=db_path,
    )
    return apply_preview_decision_with_filter(
        payload,
        config,
        vacancy_filter,
        db_path,
    )


def apply_preview_decision_with_filter(
    payload: Any,
    config: configparser.ConfigParser,
    vacancy_filter: VacancyFilter,
    db_path: Path,
) -> Any:
    if isinstance(payload, list):
        return [
            apply_preview_decision_with_filter(
                item,
                config,
                vacancy_filter,
                db_path,
            )
            for item in payload
        ]
    if not isinstance(payload, dict):
        raise ValueError("Preview payload must be a JSON object or list.")

    preview = payload.get("preview")
    if not isinstance(preview, dict):
        preview = payload
    return record_preview_filter(
        {
            **payload,
            **decide_preview_with_filter(
                preview,
                config,
                vacancy_filter,
                db_path,
            ),
        },
        db_path,
    )


def write_result(result: Any, output_path: str) -> None:
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if output_path == "-":
        print(text, end="")
    else:
        Path(output_path).write_text(text, encoding="utf-8")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Filter a LinkedIn vacancy.")
    subparsers = parser.add_subparsers(dest="stage", required=True)

    preview = subparsers.add_parser("preview")
    preview.add_argument("--job-id", required=True)
    preview.add_argument("--source-url", required=True)
    preview.add_argument("--title", required=True)
    preview.add_argument("--company", default="")
    preview.add_argument("--config", default=str(DEFAULT_PREVIEW_CONFIG))
    preview.add_argument("--db", default=str(DEFAULT_DB))

    content = subparsers.add_parser("content")
    content.add_argument("--source", required=True)
    content.add_argument("--job-id", required=True)
    content.add_argument("--title", default="")
    content.add_argument("--output", "-o", default="-")
    content.add_argument("--config", default=str(DEFAULT_CONTENT_CONFIG))
    content.add_argument("--language-config", default=str(DEFAULT_LANGUAGE_CONFIG))
    content.add_argument("--resume", default=str(DEFAULT_RESUME_CONFIG))
    content.add_argument("--db", default=str(DEFAULT_DB))
    args = parser.parse_args()

    if args.stage == "preview":
        payload: Any = {
            "job_id": args.job_id,
            "source_url": args.source_url,
            "title": args.title,
            "company": args.company,
        }
        write_result(
            apply_preview_decision(payload, Path(args.config), Path(args.db)),
            "-",
        )
        return

    db_path = ensure_migrated_database(Path(args.db))
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        text = load_readable_text(connection, args.source, args.job_id)
        filter_result = VacancyFilter(
            content_config_path=Path(args.config),
            language_config_path=Path(args.language_config),
            resume_path=Path(args.resume),
            db_path=db_path,
        ).filter_text(args.title, text)
        if filter_result.rejected:
            save_content_filter_rejection(
                connection,
                args.source,
                args.job_id,
                rule=filter_result.rule,
                matched_text=filter_result.match,
                keyword_patterns=filter_result.keyword_patterns,
            )
        result = content_response(filter_result)
        connection.commit()
    write_result(result, args.output)


if __name__ == "__main__":
    main()
