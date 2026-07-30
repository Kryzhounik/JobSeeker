"""Codex App Server transport facade."""

from __future__ import annotations

from configparser import ConfigParser
from pathlib import Path

from codex_proxy.app_server import run_turn
from codex_proxy import server
from codex_proxy.backend import Result


def call(
    *,
    config: ConfigParser,
    prompt: str,
    model: str,
    reasoning_effort: str,
    cwd: Path,
) -> Result:
    owned_server = server.start(quiet=True)
    try:
        sandbox = config.get(
            "proxy", "sandbox", fallback="workspaceWrite"
        ).strip()
        writable_roots = [
            (cwd / path.strip()).resolve()
            for path in config.get(
                "proxy", "add_dirs", fallback=""
            ).split(",")
            if path.strip()
        ]
        return run_turn(
            url=config.get("proxy", "app_server_url").strip(),
            prompt=prompt,
            model=model,
            reasoning_effort=reasoning_effort,
            cwd=cwd,
            sandbox=sandbox,
            writable_roots=writable_roots,
            approval_policy=config.get(
                "proxy", "approval_policy", fallback="never"
            ).strip(),
            ephemeral=config.getboolean(
                "proxy", "ephemeral", fallback=True
            ),
        )
    finally:
        if owned_server:
            server.stop(quiet=True)
