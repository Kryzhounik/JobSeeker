"""Validate JSON against the small JSON Schema subset used by Seeker."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys
from typing import Any


class SchemaValidationError(ValueError):
    """Raised when a value does not match its project JSON schema."""


class SchemaResolver:
    def __init__(self) -> None:
        self._documents: dict[Path, dict[str, Any]] = {}

    def load(self, path: Path) -> dict[str, Any]:
        resolved = path.resolve()
        if resolved not in self._documents:
            value = json.loads(resolved.read_text(encoding="utf-8"))
            if not isinstance(value, dict):
                raise SchemaValidationError(f"schema is not an object: {resolved}")
            self._documents[resolved] = value
        return self._documents[resolved]

    def resolve(self, ref: str, current_path: Path) -> tuple[dict[str, Any], Path]:
        document_name, _, fragment = ref.partition("#")
        target_path = (
            (current_path.parent / document_name).resolve()
            if document_name
            else current_path.resolve()
        )
        target: Any = self.load(target_path)
        if fragment:
            if not fragment.startswith("/"):
                raise SchemaValidationError(f"unsupported schema reference: {ref}")
            for raw_part in fragment[1:].split("/"):
                part = raw_part.replace("~1", "/").replace("~0", "~")
                if not isinstance(target, dict) or part not in target:
                    raise SchemaValidationError(f"unresolved schema reference: {ref}")
                target = target[part]
        if not isinstance(target, dict):
            raise SchemaValidationError(f"schema reference is not an object: {ref}")
        return target, target_path


def _fail(value_path: str, message: str) -> None:
    raise SchemaValidationError(f"{value_path}: {message}")


def _validate_type(value: Any, expected: str, value_path: str) -> None:
    matches = {
        "object": isinstance(value, dict),
        "array": isinstance(value, list),
        "string": isinstance(value, str),
        "integer": isinstance(value, int) and not isinstance(value, bool),
        "number": isinstance(value, (int, float)) and not isinstance(value, bool),
        "boolean": isinstance(value, bool),
        "null": value is None,
    }.get(expected)
    if matches is None:
        _fail(value_path, f"unsupported schema type {expected!r}")
    if not matches:
        _fail(value_path, f"expected {expected}, got {type(value).__name__}")


def _validate(
    value: Any,
    schema: dict[str, Any],
    schema_path: Path,
    resolver: SchemaResolver,
    value_path: str,
) -> set[str]:
    evaluated_properties: set[str] = set()

    ref = schema.get("$ref")
    if ref is not None:
        referenced, referenced_path = resolver.resolve(str(ref), schema_path)
        evaluated_properties |= _validate(
            value, referenced, referenced_path, resolver, value_path
        )

    for part in schema.get("allOf", []):
        evaluated_properties |= _validate(value, part, schema_path, resolver, value_path)

    if "const" in schema and value != schema["const"]:
        _fail(value_path, f"expected constant {schema['const']!r}")
    if "enum" in schema and value not in schema["enum"]:
        _fail(value_path, f"value {value!r} is not in {schema['enum']!r}")

    if "not" in schema:
        try:
            _validate(value, schema["not"], schema_path, resolver, value_path)
        except SchemaValidationError:
            pass
        else:
            _fail(value_path, "value matches a forbidden schema")

    expected_type = schema.get("type")
    if expected_type is not None:
        if not isinstance(expected_type, str):
            _fail(value_path, "schema type lists are not supported")
        _validate_type(value, expected_type, value_path)

    if isinstance(value, str):
        if "minLength" in schema and len(value) < int(schema["minLength"]):
            _fail(value_path, f"length must be at least {schema['minLength']}")

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if "minimum" in schema and value < schema["minimum"]:
            _fail(value_path, f"must be at least {schema['minimum']}")
        if "maximum" in schema and value > schema["maximum"]:
            _fail(value_path, f"must be at most {schema['maximum']}")

    if isinstance(value, list) and "items" in schema:
        for index, item in enumerate(value):
            _validate(item, schema["items"], schema_path, resolver, f"{value_path}[{index}]")

    if isinstance(value, dict):
        required = schema.get("required", [])
        missing = [name for name in required if name not in value]
        if missing:
            _fail(value_path, f"missing required properties: {', '.join(missing)}")

        properties = schema.get("properties", {})
        for name, property_schema in properties.items():
            if name in value:
                _validate(
                    value[name],
                    property_schema,
                    schema_path,
                    resolver,
                    f"{value_path}.{name}",
                )
                evaluated_properties.add(name)

        extras = set(value) - set(properties)
        additional = schema.get("additionalProperties")
        if additional is False and extras:
            _fail(value_path, f"unexpected properties: {', '.join(sorted(extras))}")
        if isinstance(additional, dict):
            for name in extras:
                _validate(
                    value[name],
                    additional,
                    schema_path,
                    resolver,
                    f"{value_path}.{name}",
                )
                evaluated_properties.add(name)

        if schema.get("unevaluatedProperties") is False:
            unevaluated = set(value) - evaluated_properties
            if unevaluated:
                _fail(
                    value_path,
                    f"unevaluated properties: {', '.join(sorted(unevaluated))}",
                )

    return evaluated_properties


def validate_json(value: Any, schema_path: Path) -> None:
    resolver = SchemaResolver()
    resolved_path = schema_path.resolve()
    _validate(value, resolver.load(resolved_path), resolved_path, resolver, "$")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Validate UTF-8 JSON from stdin against a project schema."
    )
    parser.add_argument("--schema", required=True)
    parser.add_argument("--output", help="Write the validated JSON to this file.")
    args = parser.parse_args()

    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    try:
        value = json.load(sys.stdin)
        validate_json(value, Path(args.schema))
    except (json.JSONDecodeError, OSError, SchemaValidationError) as error:
        print(f"validation failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error

    rendered = json.dumps(value, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered, encoding="utf-8")
    else:
        sys.stdout.reconfigure(encoding="utf-8") if hasattr(sys.stdout, "reconfigure") else None
        sys.stdout.write(rendered)


if __name__ == "__main__":
    main()
