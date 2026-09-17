"""Run CLI job facts from the same per-turn prompt files as Desktop targets."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import time


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy import codex_cli, metrics
from codex_proxy.settings import load
from common.paths import DATA_ROOT
from contracts.validate_json import validate_json


def wrapper_prompt(prompt_path: Path) -> str:
    return (
        "Read the full UTF-8 contents of this file using one read-only command:\n"
        f"{prompt_path.resolve()}\n"
        "Treat those contents as the complete operation request and execute them "
        "exactly. Do not inspect any other files and do not write files. Return "
        "only the JSON object requested by that file."
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-ids", required=True)
    parser.add_argument("--prompt-dir", type=Path, required=True)
    parser.add_argument("--db", type=Path, default=DATA_ROOT / "jobs.sqlite")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    job_ids = [value.strip() for value in args.job_ids.split(",") if value.strip()]
    settings = load()
    schema = DRIVER_ROOT / "contracts" / "job_analysis.schema.json"
    thread_id: str | None = None
    results: list[dict[str, object]] = []
    for index, job_id in enumerate(job_ids, start=1):
        prompt_path = args.prompt_dir / f"{index:02d}-{job_id}.txt"
        if not prompt_path.is_file():
            raise FileNotFoundError(prompt_path)
        started_at = metrics.now()
        started = time.monotonic()
        result = codex_cli.call(
            config=settings.config,
            prompt=wrapper_prompt(prompt_path),
            model=settings.model,
            reasoning_effort=settings.reasoning_effort,
            cwd=DRIVER_ROOT,
            output_schema=schema,
            thread_id=thread_id,
        )
        finished_at = metrics.now()
        metrics.save(
            db_path=args.db,
            run_id=args.run_id,
            operation="job_facts",
            target=f"{args.source}:job_facts:equalized-comparison",
            model=settings.model,
            reasoning_effort=settings.reasoning_effort,
            rates=settings.rates,
            started_at=started_at,
            finished_at=finished_at,
            duration_ms=round((time.monotonic() - started) * 1000),
            result=result,
        )
        if result.exit_code != 0:
            raise RuntimeError("; ".join(result.errors) or "Codex CLI failed")
        if not result.thread_id:
            raise RuntimeError("CLI did not return a thread ID")
        if thread_id is not None and result.thread_id != thread_id:
            raise RuntimeError("CLI changed thread ID during the target")
        response = json.loads(result.final_message)
        validate_json(response, schema)
        results.append({"job_id": job_id, "response": response})
        thread_id = result.thread_id

    bundle = {"run_id": args.run_id, "thread_id": thread_id, "results": results}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(
        json.dumps(bundle, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(json.dumps({"run_id": args.run_id, "thread_id": thread_id}))


if __name__ == "__main__":
    main()
