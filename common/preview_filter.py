from __future__ import annotations

import argparse
import configparser
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CONFIG = ROOT / "common" / "config" / "preview_filter.ini"


def load_config(path: Path = DEFAULT_CONFIG) -> configparser.ConfigParser:
    config = configparser.ConfigParser()
    config.read(path, encoding="utf-8")
    return config


def csv_values(value: str) -> list[str]:
    return [item.strip() for item in value.split(",") if item.strip()]


def enabled(config: configparser.ConfigParser) -> bool:
    return config.get("filters", "enabled", fallback="on").strip().lower() not in {
        "off",
        "false",
        "no",
        "0",
    }


def blocked_terms(config: configparser.ConfigParser) -> list[str]:
    return csv_values(config.get("title", "blocked_terms", fallback=""))


def matches_term(text: str, term: str) -> bool:
    haystack = text.lower()
    needle = term.lower().strip()
    if not needle:
        return False

    if any(not char.isalnum() for char in needle):
        return needle in haystack

    return re.search(rf"(?<![a-z0-9]){re.escape(needle)}(?![a-z0-9])", haystack) is not None


def decide_preview(
    preview: dict[str, Any],
    config_path: Path = DEFAULT_CONFIG,
) -> dict[str, Any]:
    config = load_config(config_path)
    title = str(preview.get("title") or "").strip()

    if not enabled(config):
        return {
            "preview_decision": "open",
            "preview_reason": "preview filter disabled",
            "preview_blocked_terms": [],
        }

    matches = [term for term in blocked_terms(config) if matches_term(title, term)]
    if matches:
        return {
            "preview_decision": "skip",
            "preview_reason": "title blocked: " + ", ".join(matches),
            "preview_blocked_terms": matches,
        }

    when_unsure = config.get("decision", "when_unsure", fallback="open").strip().lower()
    return {
        "preview_decision": "open" if when_unsure != "skip" else "skip",
        "preview_reason": "no preview skip signals",
        "preview_blocked_terms": [],
    }


def apply_decision(payload: Any, config_path: Path) -> Any:
    if isinstance(payload, list):
        return [apply_decision(item, config_path) for item in payload]

    if not isinstance(payload, dict):
        raise ValueError("Preview payload must be a JSON object or list.")

    preview = payload.get("preview")
    if not isinstance(preview, dict):
        preview = payload

    result = decide_preview(preview, config_path)
    return __import__("logger").record_preview_filter({**payload, **result})


def load_payload(input_path: str, title: str) -> Any:
    if title:
        return {"title": title}

    if input_path == "-":
        return json.load(sys.stdin)

    return json.loads(Path(input_path).read_text(encoding="utf-8"))


def write_payload(payload: Any, output_path: str) -> None:
    text = json.dumps(payload, ensure_ascii=False, indent=2) + "\n"
    if output_path == "-":
        print(text, end="")
        return
    Path(output_path).write_text(text, encoding="utf-8")


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")

    parser = argparse.ArgumentParser(description="Apply cheap preview filter to job cards.")
    parser.add_argument("--input", "-i", default="-", help="Preview JSON file, list, or stdin.")
    parser.add_argument("--output", "-o", default="-", help="Output JSON file or stdout.")
    parser.add_argument("--title", default="", help="Quick check for a single title.")
    parser.add_argument("--config", default=str(DEFAULT_CONFIG))
    args = parser.parse_args()

    payload = load_payload(args.input, args.title)
    result = apply_decision(payload, Path(args.config))
    write_payload(result, args.output)


if __name__ == "__main__":
    main()
