"""Load trusted Python rule catalogs afresh for each filter operation."""

from __future__ import annotations

from pathlib import Path
import runpy


def load_rule_module(
    path: Path,
    required_names: tuple[str, ...],
) -> dict[str, object]:
    rules = runpy.run_path(str(path.resolve()))
    missing = [name for name in required_names if name not in rules]
    if missing:
        raise ValueError(f"Missing rules in {path.name}: {', '.join(missing)}")
    return rules
