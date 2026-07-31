"""Codex proxy settings."""

from __future__ import annotations

from configparser import ConfigParser
from pathlib import Path
from typing import NamedTuple


CONFIG_PATH = Path(__file__).with_name("config.ini")


class Settings(NamedTuple):
    config: ConfigParser
    model: str
    reasoning_effort: str
    rates: tuple[float, float, float]


class OperationSettings(NamedTuple):
    instruction: Path
    output_schema: Path
    contexts: tuple[Path, ...]
    merge_input: bool


def load() -> Settings:
    config = ConfigParser()
    if not config.read(CONFIG_PATH, encoding="utf-8"):
        raise RuntimeError(f"Missing Codex proxy config: {CONFIG_PATH}")

    model = config.get("proxy", "model")
    reasoning_effort = config.get("proxy", "reasoning_effort").strip()
    if not reasoning_effort:
        raise RuntimeError("Missing Codex proxy reasoning_effort.")

    section = f"model:{model}"
    if not config.has_section(section):
        raise RuntimeError(f"Missing rate config for model: {model}")
    rates = (
        config.getfloat(section, "input_credit_rate"),
        config.getfloat(section, "cached_input_credit_rate"),
        config.getfloat(section, "output_credit_rate"),
    )
    if rates[0] <= 0 or rates[1] < 0 or rates[2] < 0:
        raise RuntimeError(f"Invalid rate config for model: {model}")
    return Settings(config, model, reasoning_effort, rates)


def load_operation(config: ConfigParser, name: str) -> OperationSettings:
    section = f"operation:{name}"
    if not config.has_section(section):
        raise RuntimeError(f"Unknown Codex operation: {name}")

    root = CONFIG_PATH.parent.parent

    def project_path(key: str) -> Path:
        value = config.get(section, key).strip()
        if not value:
            raise RuntimeError(f"Missing {section}.{key}")
        return (root / value).resolve()

    contexts = tuple(
        (root / value.strip()).resolve()
        for value in config.get(section, "contexts", fallback="").split(",")
        if value.strip()
    )
    return OperationSettings(
        instruction=project_path("instruction"),
        output_schema=project_path("output_schema"),
        contexts=contexts,
        merge_input=config.getboolean(section, "merge_input", fallback=False),
    )
