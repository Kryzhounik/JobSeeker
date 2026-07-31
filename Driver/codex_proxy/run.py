"""Run one metered Codex operation through the selected backend."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from codex_proxy import codex_cli as codex_backend
from codex_proxy import metrics
from codex_proxy.settings import load, load_operation


def operation_prompt(
    instruction_path: Path,
    input_path: Path,
    context_paths: tuple[Path, ...],
) -> str:
    sections = [
        "Perform exactly one isolated data operation.",
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


def write_result(
    final_message: str,
    input_path: Path,
    output_path: Path,
    merge_input: bool,
) -> None:
    result = json.loads(final_message)
    if not isinstance(result, dict):
        raise ValueError("Codex operation did not return a JSON object")
    if merge_input:
        base = json.loads(input_path.read_text(encoding="utf-8"))
        if not isinstance(base, dict):
            raise ValueError(f"Input is not a JSON object: {input_path}")
        base.update(result)
        result = base
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--operation", required=True)
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--target", default="")
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    args = parser.parse_args()

    settings = load()
    operation = load_operation(settings.config, args.operation)
    input_path = Path(args.input).resolve()
    output_path = Path(args.output).resolve()
    started_at = metrics.now()
    started = time.monotonic()
    result = codex_backend.call(
        config=settings.config,
        prompt=operation_prompt(
            operation.instruction,
            input_path,
            operation.contexts,
        ),
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
        cwd=ROOT,
        output_schema=operation.output_schema,
    )
    if result.exit_code == 0:
        try:
            write_result(
                result.final_message,
                input_path,
                output_path,
                operation.merge_input,
            )
        except (OSError, ValueError, json.JSONDecodeError) as error:
            result = result._replace(
                exit_code=1,
                errors=[*result.errors, str(error)],
            )
    finished_at = metrics.now()
    metrics.save(
        db_path=Path(args.db),
        run_id=args.run_id,
        operation=args.operation,
        target=args.target or str(input_path),
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
        rates=settings.rates,
        started_at=started_at,
        finished_at=finished_at,
        duration_ms=round((time.monotonic() - started) * 1000),
        result=result,
    )
    if result.final_message:
        print(result.final_message)
    raise SystemExit(result.exit_code)


if __name__ == "__main__":
    main()
