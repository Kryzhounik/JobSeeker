"""Run an ordered job-facts comparison sample through one Codex CLI target."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from analyzer.job_facts.load_input import load_input
from codex_proxy.metrics_proxy import run
from common.paths import DATA_ROOT
from contracts.validate_json import validate_json


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-ids", required=True)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    job_ids = [value.strip() for value in args.job_ids.split(",") if value.strip()]
    if not job_ids:
        raise SystemExit("--job-ids must contain at least one ID")

    thread_id: str | None = None
    results: list[dict[str, object]] = []
    for job_id in job_ids:
        input_value = load_input(args.source, job_id)
        result = run(
            run_id=args.run_id,
            operation="job_facts",
            target=f"{args.source}:job_facts:comparison",
            instruction_path=(
                DRIVER_ROOT / "analyzer" / "job_facts" / "extract.md"
            ),
            input_text=json.dumps(input_value, ensure_ascii=False),
            input_name=f"{args.source}-{job_id}.json",
            context_paths=(
                DRIVER_ROOT / "analyzer" / "job_facts" / "language_levels.md",
                DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
            ),
            output_schema=DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
            db_path=Path(args.db),
            thread_id=thread_id,
        )
        if result.exit_code != 0:
            details = "; ".join(result.errors) or "Codex CLI exited unsuccessfully"
            raise RuntimeError(f"Job facts failed for {args.source}:{job_id}: {details}")
        if not result.thread_id:
            raise RuntimeError(f"Missing CLI thread ID for {args.source}:{job_id}")
        if thread_id is not None and result.thread_id != thread_id:
            raise RuntimeError(
                f"CLI thread changed from {thread_id} to {result.thread_id}"
            )

        response = json.loads(result.final_message)
        validate_json(
            response,
            DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
        )
        results.append({"job_id": job_id, "response": response})
        thread_id = result.thread_id

    bundle = {
        "run_id": args.run_id,
        "thread_id": thread_id,
        "results": results,
    }
    rendered = json.dumps(bundle, ensure_ascii=False, indent=2) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    main()
