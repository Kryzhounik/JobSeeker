"""Codex CLI transport."""

from __future__ import annotations

from configparser import ConfigParser
import json
import os
from pathlib import Path
import shutil
import subprocess

from codex_proxy.backend import Result
from codex_proxy.output_schema import codex_schema_file


USAGE_KEYS = (
    "input_tokens",
    "cached_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)


def hidden_process_flags() -> int:
    return subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def call(
    *,
    config: ConfigParser,
    prompt: str,
    model: str,
    reasoning_effort: str,
    cwd: Path,
    output_schema: Path,
) -> Result:
    executable = shutil.which("codex.cmd") or shutil.which("codex")
    if not executable:
        raise RuntimeError("Codex CLI was not found on PATH.")

    configured_args: list[str] = []
    if config.getboolean("proxy", "ignore_user_config", fallback=True):
        configured_args.append("--ignore-user-config")
    if config.getboolean("proxy", "ephemeral", fallback=True):
        configured_args.append("--ephemeral")
    configured_args.extend((
        "--config",
        f"model_reasoning_effort={json.dumps(reasoning_effort)}",
    ))

    sandbox = config.get("proxy", "sandbox", fallback="").strip()
    sandbox = {
        "workspaceWrite": "workspace-write",
        "readOnly": "read-only",
        "dangerFullAccess": "danger-full-access",
    }.get(sandbox, sandbox)
    if sandbox:
        configured_args.extend(("--sandbox", sandbox))
    for path in config.get("proxy", "add_dirs", fallback="").split(","):
        if path := path.strip():
            configured_args.extend(("--add-dir", path))

    usage = dict.fromkeys(USAGE_KEYS, 0)
    thread_id = ""
    final_message = ""
    errors: list[str] = []

    with codex_schema_file(output_schema) as schema_path:
        command = [
            executable,
            "exec",
            "--json",
            "--model",
            model,
            *configured_args,
            "--output-schema",
            str(schema_path),
            "-",
        ]
        process = subprocess.run(
            command,
            cwd=cwd,
            input=prompt,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            creationflags=hidden_process_flags(),
            check=False,
        )

    for line in process.stdout.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            errors.append(line.strip())
            continue

        event_type = event.get("type")
        if event_type == "thread.started":
            thread_id = str(event.get("thread_id") or "")
        elif event_type == "turn.completed":
            values = event.get("usage") or {}
            usage = {key: int(values.get(key) or 0) for key in USAGE_KEYS}
        elif event_type == "item.completed":
            item = event.get("item") or {}
            if item.get("type") == "agent_message":
                final_message = str(item.get("text") or "")
        elif event_type in {"turn.failed", "error"}:
            errors.append(json.dumps(event, ensure_ascii=False))
    if process.stderr.strip():
        errors.append(process.stderr.strip())

    return Result(
        command,
        process.returncode,
        thread_id,
        final_message,
        usage,
        errors,
    )
