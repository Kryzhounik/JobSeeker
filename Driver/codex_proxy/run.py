"""Run one metered Codex operation through the selected backend."""

from __future__ import annotations

import argparse
from pathlib import Path
import sys
import time


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from common.paths import DATA_ROOT
from codex_proxy import codex_cli as codex_backend
from codex_proxy import metrics
from codex_proxy.settings import load


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--operation", required=True)
    parser.add_argument("--target", default="")
    parser.add_argument("--db", default=str(DATA_ROOT / "jobs.sqlite"))
    parser.add_argument("codex_args", nargs=argparse.REMAINDER)
    args = parser.parse_args()

    prompt_parts = (
        args.codex_args[1:]
        if args.codex_args[:1] == ["--"]
        else args.codex_args
    )
    if not prompt_parts:
        raise SystemExit("Pass the Codex prompt after --.")

    settings = load()
    started_at = metrics.now()
    started = time.monotonic()
    result = codex_backend.call(
        config=settings.config,
        prompt=" ".join(prompt_parts),
        model=settings.model,
        reasoning_effort=settings.reasoning_effort,
        cwd=ROOT,
    )
    finished_at = metrics.now()
    metrics.save(
        db_path=Path(args.db),
        run_id=args.run_id,
        operation=args.operation,
        target=args.target,
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
