"""Reject explicit hard skill mismatches found in readable vacancy text."""

from __future__ import annotations

import argparse
import configparser
import json
import re
import sys
from pathlib import Path
from typing import Any


DEFAULT_CONFIG = Path(__file__).with_name("linkedin_content_filter.ini")


def load_config(path: Path = DEFAULT_CONFIG) -> configparser.ConfigParser:
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
    return [re.compile(pattern, re.IGNORECASE) for pattern in configured_values(config, section)]


def technology_pattern(name: str) -> re.Pattern[str]:
    return re.compile(
        rf"(?<![\w+#.]){re.escape(name)}(?![\w+#.])",
        re.IGNORECASE,
    )


def content_units(text: str) -> list[str]:
    lines = [line.strip() for line in text.splitlines()]
    units = [line for line in lines if line]
    units.extend(
        f"{line} {lines[index + 1]}"
        for index, line in enumerate(lines[:-1])
        if line and lines[index + 1]
    )
    return units


def decide_content(
    text: str,
    config_path: Path = DEFAULT_CONFIG,
    *,
    title: str = "",
) -> dict[str, Any]:
    config = load_config(config_path)
    matched_pass_words = [
        name
        for name in configured_names(config, "pass_words")
        if technology_pattern(name).search(title)
    ]
    if matched_pass_words:
        return {
            "content_decision": "analyze",
            "content_reason": "title pass word: " + ", ".join(matched_pass_words),
            "content_rule": "title_pass_word",
            "content_match": title,
        }

    if not enabled(config):
        return {
            "content_decision": "analyze",
            "content_reason": "content filter disabled",
            "content_rule": "",
            "content_match": "",
        }

    technologies = [
        (name, technology_pattern(name))
        for name in configured_names(config, "blocked_technologies")
    ]
    hard_signals = configured_patterns(config, "hard_requirement_signals")
    optional_signals = configured_patterns(config, "optional_signals")
    alternative_signals = configured_patterns(config, "alternative_signals")

    for unit in content_units(text):
        matched_technologies = [name for name, pattern in technologies if pattern.search(unit)]
        matched_signals = [pattern.pattern for pattern in hard_signals if pattern.search(unit)]
        if not matched_technologies or not matched_signals:
            continue
        if any(pattern.search(unit) for pattern in optional_signals):
            continue
        if any(pattern.search(unit) for pattern in alternative_signals):
            continue

        return {
            "content_decision": "skip",
            "content_reason": "hard requirement for blocked technology: "
            + ", ".join(matched_technologies),
            "content_rule": "hard_blocked_technology",
            "content_match": unit,
            "content_technologies": matched_technologies,
            "content_signals": matched_signals,
        }

    return {
        "content_decision": "analyze",
        "content_reason": "no content skip signals",
        "content_rule": "",
        "content_match": "",
    }


def write_result(result: dict[str, Any], output_path: str) -> None:
    text = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if output_path == "-":
        print(text, end="")
    else:
        Path(output_path).write_text(text, encoding="utf-8")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Filter a readable LinkedIn vacancy.")
    parser.add_argument("--input", "-i", required=True, help="Readable text file.")
    parser.add_argument("--title", default="", help="Preview title used for pass words.")
    parser.add_argument("--output", "-o", default="-", help="Result JSON or stdout.")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = parser.parse_args()

    text = Path(args.input).read_text(encoding="utf-8")
    write_result(decide_content(text, Path(args.config), title=args.title), args.output)


if __name__ == "__main__":
    main()
