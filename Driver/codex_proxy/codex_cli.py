"""Codex CLI transport."""

from __future__ import annotations

from configparser import ConfigParser
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
from uuid import uuid4

from common.paths import DATA_ROOT
from codex_proxy.backend import Result
from codex_proxy.output_schema import codex_schema_file


USAGE_KEYS = (
    "input_tokens",
    "cached_input_tokens",
    "output_tokens",
    "reasoning_output_tokens",
)

TRANSPORT_INSTRUCTIONS = Path(__file__).with_name("transport_instructions.md")
UNUSED_FEATURES = (
    "shell_tool",
    "shell_snapshot",
    "multi_agent",
    "apps",
    "plugins",
    "skill_search",
    "goals",
    "sleep_tool",
    "view_image",
    "image_generation",
    "computer_use",
    "browser_use",
    "workspace_dependencies",
    "tool_suggest",
)


def hidden_process_flags() -> int:
    return subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0


def _redact(text: str) -> str:
    for name, value in os.environ.items():
        if value and re.search(r"(?:KEY|TOKEN|PASSWORD|SECRET)$", name, re.IGNORECASE):
            text = text.replace(value, "[REDACTED]")
    text = re.sub(r"(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]+", r"\1 [REDACTED]", text)
    text = re.sub(r"\b(?:sk-[A-Za-z0-9_-]{8,}|eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+)",
                  "[REDACTED]", text)
    keys = (r"authorization|proxy-authorization|cookie|set-cookie|x-api-key|api[_-]?key|"
            r"access[_-]?token|refresh[_-]?token|id[_-]?token|token|password|client[_-]?secret")
    text = re.sub(
        rf'''(?i)((?:{keys})(?:\\*["'])?\s*[:=]\s*)(\\*["'])(.*?)(\2)''',
        lambda match: match[1] + match[2] + "[REDACTED]" + match[4], text,
    )
    return re.sub(rf"(?im)((?:{keys})\s*[:=]\s*)[^\r\n]*", r"\1[REDACTED]", text)


def _save_failure_logs(stdout: str, stderr: str) -> Path:
    name = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-") + uuid4().hex[:8]
    directory = DATA_ROOT / "logs" / "codex" / name
    directory.mkdir(parents=True)
    (directory / "stdout.log").write_text(_redact(stdout), encoding="utf-8")
    (directory / "stderr.log").write_text(_redact(stderr), encoding="utf-8")
    return directory


def call(
    *,
    config: ConfigParser,
    prompt: str,
    model: str,
    reasoning_effort: str,
    cwd: Path,
    output_schema: Path,
    thread_id: str | None = None,
    browser_cdp_endpoint: str | None = None,
) -> Result:
    executable = shutil.which("codex.cmd") or shutil.which("codex")
    if not executable:
        raise RuntimeError("Codex CLI was not found on PATH.")

    thread_id = (thread_id or "").strip()
    ephemeral = config.getboolean("proxy", "ephemeral", fallback=True)
    if thread_id and ephemeral:
        raise ValueError("Cannot resume a Codex session while ephemeral mode is enabled.")

    configured_args: list[str] = []
    if config.getboolean("proxy", "ignore_user_config", fallback=True):
        configured_args.append("--ignore-user-config")
    if ephemeral:
        configured_args.append("--ephemeral")
    configured_args.extend((
        "--config",
        f"model_reasoning_effort={json.dumps(reasoning_effort)}",
    ))
    if config.getboolean("proxy", "lean_context", fallback=True):
        overrides = {
            "model_instructions_file": TRANSPORT_INSTRUCTIONS.resolve().as_posix(),
            "skills.include_instructions": False,
            "skills.bundled.enabled": False,
            "agents.enabled": False,
            "include_collaboration_mode_instructions": False,
            "include_permissions_instructions": False,
            "include_apps_instructions": False,
            "project_doc_max_bytes": 0,
            "web_search": "disabled",
            "tools.update_plan.enabled": False,
            "tools.experimental_request_user_input.enabled": False,
        }
        for key, value in overrides.items():
            configured_args.extend(("--config", f"{key}={json.dumps(value)}"))
        for feature in UNUSED_FEATURES:
            configured_args.extend(("--disable", feature))
    if browser_cdp_endpoint:
        node = shutil.which("node")
        if not node:
            raise RuntimeError("Node.js was not found on PATH for Playwright MCP.")
        mcp_script = Path(__file__).resolve().parent / "node_modules/@playwright/mcp/cli.js"
        if not mcp_script.is_file():
            raise RuntimeError(
                "Playwright MCP is not installed. Run npm ci --prefix Driver/codex_proxy "
                "from the Seeker project root."
            )
        args = [str(mcp_script), "--cdp-endpoint", browser_cdp_endpoint]
        server_config = "{command = " + json.dumps(node) + ", args = [" + ", ".join(
            json.dumps(value) for value in args
        ) + "], default_tools_approval_mode = \"approve\", required = true, startup_timeout_sec = 45}"
        configured_args.extend((
            "--config",
            "mcp_servers.playwright=" + server_config,
        ))

    if not thread_id:
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
    result_thread_id = thread_id
    final_message = ""
    errors: list[str] = []

    with codex_schema_file(output_schema) as schema_path:
        command = [
            executable,
            "exec",
            *(["resume"] if thread_id else []),
            "--json",
            "--model",
            model,
            *configured_args,
            "--output-schema",
            str(schema_path),
            *([thread_id] if thread_id else []),
            "-",
        ]
        process_env = os.environ.copy()
        process_env["RUST_LOG"] = "warn,codex_api=debug,codex_core::client=debug"
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
            env=process_env,
        )

    for line in process.stdout.splitlines():
        try:
            event = json.loads(line)
        except json.JSONDecodeError:
            errors.append(line.strip())
            continue

        event_type = event.get("type")
        if event_type == "thread.started":
            started_thread_id = str(event.get("thread_id") or "")
            if started_thread_id:
                result_thread_id = started_thread_id
        elif event_type == "turn.completed":
            values = event.get("usage") or {}
            usage = {key: int(values.get(key) or 0) for key in USAGE_KEYS}
        elif event_type == "item.completed":
            item = event.get("item") or {}
            if item.get("type") == "agent_message":
                final_message = str(item.get("text") or "")
        elif event_type in {"turn.failed", "error"}:
            errors.append(json.dumps(event, ensure_ascii=False))
    errors = [_redact(error) for error in errors]
    if process.returncode != 0:
        if not errors:
            errors.append(_redact(process.stderr.strip().splitlines()[-1])[:500]
                          if process.stderr.strip() else f"Codex CLI exited with code {process.returncode}.")
        try:
            errors.append(f"CLI diagnostics: {_save_failure_logs(process.stdout, process.stderr)}")
        except OSError as error:
            errors.append(f"Cannot save CLI diagnostics: {error}")

    return Result(
        command,
        process.returncode,
        result_thread_id,
        final_message,
        usage,
        errors,
    )
