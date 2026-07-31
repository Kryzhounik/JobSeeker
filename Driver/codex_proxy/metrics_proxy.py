"""Thin metered transport for isolated Codex CLI instructions.

This module may only adapt an explicit agent-operation request to ``codex
exec``, return the final agent response, and store transport usage metrics. It
must not choose analyzer operations, interpret or merge business JSON, write
analyzed/scored files, run filters or scoring, or save vacancies.
"""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from codex_proxy import codex_cli
from codex_proxy import metrics
from codex_proxy.backend import Result
from codex_proxy.settings import load


def cli_prompt(
    instruction_path: Path,
    input_path: Path,
    context_paths: tuple[Path, ...],
) -> str:
    """Materialize an explicit operation request for an isolated CLI agent."""
    sections = [
        "Perform exactly one isolated agent operation.",
        "Do not use tools, inspect other files, run commands, or write files.",
        "Return only the JSON object required by the output schema.",
        f"\nINSTRUCTION ({instruction_path.name}):\n"
        + instruction_path.read_text(encoding="utf-8"),
        f"\nINPUT ({input_path.name}):\n"
        + input_path.read_text(encoding="utf-8"),
    ]
    sections.extend(
        f"\nCONTEXT ({path.name}):\n" + path.read_text(encoding="utf-8")
        for path in context_paths
    )
    return "\n".join(sections)


def run(
    *,
    run_id: str,
    operation: str,
    target: str,
    instruction_path: Path,
    input_path: Path,
    context_paths: tuple[Path, ...],
    output_schema: Path,
    db_path: Path = DATA_ROOT / "jobs.sqlite",
) -> Result:
    """Invoke one CLI instruction and persist only its usage metrics."""
    settings = load()
    started_at = metrics.now()
    started = time.monotonic()
    result = codex_cli.call(
        config=settings.config,
        prompt=cli_prompt(
            instruction_path.resolve(),
            input_path.resolve(),
            tuple(path.resolve() for path in context_paths),
        ),
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
        cwd=ROOT,
        output_schema=output_schema.resolve(),
    )
    finished_at = metrics.now()
    metrics.save(
        db_path=db_path,
        run_id=run_id,
        operation=operation,
        target=target or str(input_path.resolve()),
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
        rates=settings.rates,
        started_at=started_at,
        finished_at=finished_at,
        duration_ms=round((time.monotonic() - started) * 1000),
        result=result,
    )
    return result


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--operation", required=True)
    parser.add_argument("--target", default="")
    parser.add_argument("--instruction", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--context", action="append", default=[])
    parser.add_argument("--output-schema", required=True)
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    result = run(
        run_id=args.run_id,
        operation=args.operation,
        target=args.target,
        instruction_path=Path(args.instruction),
        input_path=Path(args.input),
        context_paths=tuple(Path(path) for path in args.context),
        output_schema=Path(args.output_schema),
        db_path=Path(args.db),
    )
    if result.final_message:
        print(result.final_message)
    raise SystemExit(result.exit_code)


if __name__ == "__main__":
    main()
