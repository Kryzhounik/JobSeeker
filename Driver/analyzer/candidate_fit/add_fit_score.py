"""Add a candidate-fit score to analyzed JSON using UTF-8 files only."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


FIT_FIELDS = {
    "candidate_fit_percent",
    "candidate_fit_reason_code",
    "candidate_fit_reason",
}


def add_fit_score(source_path: Path, fit_result: dict[str, Any], output_path: Path) -> None:
    source = json.loads(source_path.read_text(encoding="utf-8"))
    if not isinstance(source, dict):
        raise ValueError("analyzed JSON must contain an object")
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
    parser.add_argument("--result", required=True, help="Candidate-fit result JSON file.")
    parser.add_argument("--output", required=True, help="Scored JSON file.")
    args = parser.parse_args()

    fit_result = json.loads(Path(args.result).read_text(encoding="utf-8"))
    if not isinstance(fit_result, dict):
        raise ValueError("candidate-fit result must contain an object")
    add_fit_score(Path(args.input), fit_result, Path(args.output))


if __name__ == "__main__":
    main()
