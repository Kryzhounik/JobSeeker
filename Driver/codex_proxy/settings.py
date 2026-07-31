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
