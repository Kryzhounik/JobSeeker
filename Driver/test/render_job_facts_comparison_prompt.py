"""Render the exact visible prompt used by the job-facts CLI comparison."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.job_facts.load_input import load_input
from codex_proxy.metrics_proxy import cli_continuation_prompt, cli_prompt


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--continuation", action="store_true")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    input_text = json.dumps(
        load_input(args.source, args.job_id),
        ensure_ascii=False,
    )
    input_name = f"{args.source}-{args.job_id}.json"
    if args.continuation:
        rendered = cli_continuation_prompt(input_text, input_name)
    else:
        rendered = cli_prompt(
            DRIVER_ROOT / "analyzer" / "job_facts" / "extract.md",
            input_text,
            input_name,
            (
                DRIVER_ROOT
                / "analyzer"
                / "job_facts"
                / "language_levels.md",
                DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
            ),
        )
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered + "\n", encoding="utf-8")
    else:
        print(rendered)


if __name__ == "__main__":
    main()
