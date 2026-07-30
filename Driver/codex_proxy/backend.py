"""Common contract implemented by Codex transports."""

from __future__ import annotations

from typing import NamedTuple


class Result(NamedTuple):
    command: list[str]
    exit_code: int
    thread_id: str
    final_message: str
    usage: dict[str, int]
    errors: list[str]
