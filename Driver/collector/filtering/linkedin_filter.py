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
from collector.filtering.language_requirements import extract_language_requirements
from db.companies import is_company_blacklisted
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


YEARS_EXPRESSION = (
    r"(?:[2-9]|[1-9]\d+)"
    r"(?:\+|\s*[-\u2013\u2014]\s*(?:[2-9]|[1-9]\d+))?"
    r"\s+years?"
)


def technology_expression(name: str) -> str:
    return rf"(?<![\w+#]){re.escape(name)}(?![\w+#])"


def technology_pattern(name: str) -> re.Pattern[str]:
    flags = 0 if name == "Go" else re.IGNORECASE
    return re.compile(technology_expression(name), flags)


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

    def filter(self, vacancy: Vacancy) -> FilterResult:
        result = self.filter_preview(vacancy.title, vacancy.company)
        if result.rejected or vacancy.text is None:
            return result
        return self.filter_text(vacancy.title, vacancy.text)

    def filter_preview(self, title: str, company: str = "") -> FilterResult:
        if not enabled(self.preview_config):
            return FilterResult(False, "preview filter disabled")
        result = self.filter_title(title)
        if result.rejected:
            return result
        return self.filter_company(company)

    def filter_title(self, title: str) -> FilterResult:
        config = self.preview_config
        if not enabled(config):
            return FilterResult(False, "preview filter disabled")

        matches = [
            term
            for term in blocked_terms(config, self.preview_config_path)
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
        name = company.strip()
        if not name or not self.db_path.exists():
            return FilterResult(False, "no company skip signals")

        resolved_db = ensure_migrated_database(self.db_path)
        with closing(sqlite3.connect(resolved_db)) as connection:
            connection.execute("PRAGMA foreign_keys = ON")
            blacklisted = is_company_blacklisted(connection, name)
        if blacklisted:
            return FilterResult(
                True,
                f"company blacklisted: {name}",
                rule="company_blacklisted",
                match=name,
                terms=(name,),
            )
        return FilterResult(False, "no company skip signals")

    def filter_text(self, title: str, text: str) -> FilterResult:
        config = self.content_config
        pass_word_patterns = [
            (name, technology_pattern(name))
            for name in configured_names(config, "pass_words")
        ]
        pass_words = [
            name for name, pattern in pass_word_patterns if pattern.search(title)
        ]
        pass_result = None
        if pass_words:
            pass_result = FilterResult(
                False,
                "title pass word: " + ", ".join(pass_words),
                rule="title_pass_word",
                match=title,
            )

        if not pass_result and enabled(config):
            technologies = [
                (name, technology_pattern(name))
                for name in configured_names(config, "blocked_technologies")
            ]
            hard_templates = configured_values(config, "hard_requirement_templates")
            optional_signals = configured_patterns(config, "optional_signals")

            for unit, context in content_units(text):
                unit_technologies = [
                    name for name, pattern in technologies if pattern.search(unit)
                ]
                matches = [
                    (name, template)
                    for name in unit_technologies
                    for template in hard_templates
                    if requirement_pattern(template, name).search(unit)
                ]
                matched_technologies = list(dict.fromkeys(name for name, _ in matches))
                matched_signals = list(dict.fromkeys(template for _, template in matches))
                if not matched_technologies or not matched_signals:
                    continue
                if any(pattern.search(context) for _, pattern in pass_word_patterns):
                    continue
                if any(pattern.search(context) for pattern in optional_signals):
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
        if not enabled(config):
            return FilterResult(False, "technology content filter disabled")
        return FilterResult(False, "no content skip signals")

    def filter_language_requirements(self, text: str) -> FilterResult:
        requirements = extract_language_requirements(
            text,
            self.language_config_path,
        )
        if not requirements:
            return FilterResult(False, "no language skip signals")
        resume_languages = load_resume(self.resume_path)
        for requirement in requirements:
            comparison = evaluate_required_languages(
                [requirement.as_filter_row()],
                resume_languages=resume_languages,
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


DEFAULT_FILTER = VacancyFilter()


def filter_vacancy(vacancy: Vacancy) -> FilterResult:
    return DEFAULT_FILTER.filter(vacancy)


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
    if not enabled(config):
        return {
            "preview_decision": "open",
            "preview_reason": "preview filter disabled",
            "preview_blocked_terms": [],
        }

    result = VacancyFilter(
        preview_config_path=config_path,
        db_path=db_path,
    ).filter_preview(
        str(preview.get("title") or "").strip(),
        str(preview.get("company") or "").strip(),
    )
    if result.rejected:
        return {
            "preview_decision": "skip",
            "preview_reason": result.reason,
            "preview_blocked_terms": list(result.terms),
        }
    if is_duplicate_preview(preview, db_path):
        return {
            "preview_decision": "skip",
            "preview_reason": "duplicate source_job_id",
            "preview_blocked_terms": [],
        }

    when_unsure = config.get("decision", "when_unsure", fallback="open").strip().lower()
    return {
        "preview_decision": "open" if when_unsure != "skip" else "skip",
        "preview_reason": "no preview skip signals",
        "preview_blocked_terms": [],
    }


def decide_content(
    text: str,
    config_path: Path = DEFAULT_CONTENT_CONFIG,
    *,
    title: str = "",
    language_config_path: Path = DEFAULT_LANGUAGE_CONFIG,
    resume_path: Path = DEFAULT_RESUME_CONFIG,
) -> dict[str, Any]:
    result = VacancyFilter(
        content_config_path=config_path,
        language_config_path=language_config_path,
        resume_path=resume_path,
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
    if isinstance(payload, list):
        return [
            apply_preview_decision(item, config_path, db_path)
            for item in payload
        ]
    if not isinstance(payload, dict):
        raise ValueError("Preview payload must be a JSON object or list.")

    preview = payload.get("preview")
    if not isinstance(preview, dict):
        preview = payload
    return record_preview_filter(
        {**payload, **decide_preview(preview, config_path, db_path)}
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
    preview.add_argument("--input", "-i", default="-")
    preview.add_argument("--output", "-o", default="-")
    preview.add_argument("--title", default="")
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
        if args.title:
            payload: Any = {"title": args.title, "company": args.company}
        elif args.input == "-":
            payload = json.load(sys.stdin)
        else:
            payload = json.loads(Path(args.input).read_text(encoding="utf-8"))
        write_result(
            apply_preview_decision(payload, Path(args.config), Path(args.db)),
            args.output,
        )
        return

    db_path = Path(args.db)
    migrate_database(db_path)
    with sqlite3.connect(db_path) as connection:
        connection.execute("PRAGMA foreign_keys = ON")
        text = load_readable_text(connection, args.source, args.job_id)
        filter_result = VacancyFilter(
            content_config_path=Path(args.config),
            language_config_path=Path(args.language_config),
            resume_path=Path(args.resume),
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
