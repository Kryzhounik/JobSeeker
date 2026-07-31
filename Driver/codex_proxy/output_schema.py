"""Adapt the canonical JSON Schema to the Codex structured-output subset."""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
import tempfile
from typing import Any, Iterator
from contextlib import contextmanager


def _resolve_pointer(document: dict[str, Any], reference: str) -> Any:
    if not reference.startswith("#/"):
        raise ValueError(f"Only local schema references are supported: {reference}")
    value: Any = document
    for part in reference[2:].split("/"):
        value = value[part.replace("~1", "/").replace("~0", "~")]
    return deepcopy(value)


def _normalize(value: Any, document: dict[str, Any]) -> Any:
    if isinstance(value, list):
        return [_normalize(item, document) for item in value]
    if not isinstance(value, dict):
        return value

    if "$ref" in value:
        resolved = _resolve_pointer(document, str(value["$ref"]))
        overlay = {key: item for key, item in value.items() if key != "$ref"}
        if overlay:
            resolved.update(overlay)
        return _normalize(resolved, document)

    result = {
        key: _normalize(item, document)
        for key, item in value.items()
        if key not in {"$schema", "$id", "$defs", "not", "default"}
    }
    if result.get("type") == "object":
        result["additionalProperties"] = False
    return result


def codex_schema(schema_path: Path) -> dict[str, Any]:
    document = json.loads(schema_path.read_text(encoding="utf-8"))
    root: Any = document
    all_of = document.get("allOf")
    if isinstance(all_of, list) and len(all_of) == 1:
        root = all_of[0]
    normalized = _normalize(root, document)
    if not isinstance(normalized, dict):
        raise ValueError(f"Schema root must be an object: {schema_path}")
    return normalized


@contextmanager
def codex_schema_file(schema_path: Path) -> Iterator[Path]:
    handle = tempfile.NamedTemporaryFile(
        mode="w",
        encoding="utf-8",
        suffix=".json",
        delete=False,
    )
    path = Path(handle.name)
    try:
        with handle:
            json.dump(codex_schema(schema_path), handle, ensure_ascii=False)
        yield path
    finally:
        path.unlink(missing_ok=True)
