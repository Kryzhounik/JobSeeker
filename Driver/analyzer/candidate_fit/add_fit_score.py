"""Add a candidate-fit result from UTF-8 stdin to analyzed JSON."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any, TextIO


ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from contracts.validate_json import validate_json


FIT_FIELDS = {
    "candidate_fit_percent",
    "candidate_fit_reason_code",
    "candidate_fit_reason",
}


def read_json_object(stream: TextIO) -> dict[str, Any]:
    buffer = ""
    decoder = json.JSONDecoder()
    for line in stream:
        buffer += line
        try:
            text = buffer.lstrip()
            value, end = decoder.raw_decode(text)
        except json.JSONDecodeError:
            continue
        if text[end:].strip():
            raise ValueError("candidate-fit stdin contains trailing data")
        if not isinstance(value, dict):
            raise ValueError("candidate-fit result must contain an object")
        return value
    raise ValueError("candidate-fit stdin does not contain a complete JSON object")


def add_fit_score(source_path: Path, fit_result: dict[str, Any], output_path: Path) -> None:
    source = json.loads(source_path.read_text(encoding="utf-8"))
    if not isinstance(source, dict):
        raise ValueError("analyzed JSON must contain an object")
    validate_json(
        fit_result,
        ROOT / "contracts" / "candidate_fit_result.schema.json",
    )
    if set(fit_result) != FIT_FIELDS:
        raise ValueError(f"candidate-fit result must contain exactly: {sorted(FIT_FIELDS)}")

    source.update(fit_result)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(source, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Add candidate fit score to analyzed JSON.")
    parser.add_argument("--input", required=True, help="Analyzed JSON file.")
    parser.add_argument("--output", required=True, help="Scored JSON file.")
    args = parser.parse_args()

    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    fit_result = read_json_object(sys.stdin)
    add_fit_score(Path(args.input), fit_result, Path(args.output))


if __name__ == "__main__":
    main()
