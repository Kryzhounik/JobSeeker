"""Structured call boundary for Java's shared Codex client."""

from importlib import import_module
import json

from codex_proxy.errors import UsageLimitExceededError


def call(module_name: str, operation: str, arguments_json: str) -> str:
    try:
        result = getattr(import_module(module_name), operation)(*json.loads(arguments_json))
        response = {"result": result}
    except UsageLimitExceededError as error:
        response = {"error": error.payload()}
    return json.dumps(response, ensure_ascii=False)
