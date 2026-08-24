"""Merge a candidate-fit result into analyzed JSON using UTF-8 files only."""

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


def merge_json(source_path: Path, patch: dict[str, Any], output_path: Path) -> None:
    source = json.loads(source_path.read_text(encoding="utf-8"))
    if not isinstance(source, dict):
        raise ValueError("analyzed JSON must contain an object")
    if set(patch) != FIT_FIELDS:
        raise ValueError(f"candidate-fit result must contain exactly: {sorted(FIT_FIELDS)}")

    source.update(patch)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(source, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Merge candidate fit into analyzed JSON.")
    parser.add_argument("--input", required=True, help="Analyzed JSON file.")
    parser.add_argument("--result", required=True, help="Candidate-fit result JSON file.")
    parser.add_argument("--output", required=True, help="Scored JSON file.")
    args = parser.parse_args()

    patch = json.loads(Path(args.result).read_text(encoding="utf-8"))
    if not isinstance(patch, dict):
        raise ValueError("candidate-fit result must contain an object")
    merge_json(Path(args.input), patch, Path(args.output))


if __name__ == "__main__":
    main()
