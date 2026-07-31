"""One-turn client for the shared Codex App Server."""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from codex_proxy.backend import Result
from codex_proxy.app_server_experiment.json_rpc import JsonRpcConnection


USAGE_FIELDS = {
    "input_tokens": "inputTokens",
    "cached_input_tokens": "cachedInputTokens",
    "output_tokens": "outputTokens",
    "reasoning_output_tokens": "reasoningOutputTokens",
}


class AppServerClient:
    """Codex-specific thread and turn operations."""

    def __init__(self, url: str) -> None:
        self.url = url
        self.connection = JsonRpcConnection(url)

    def close(self) -> None:
        self.connection.close()

    def execute(
        self,
        prompt: str,
        model: str,
        reasoning_effort: str,
        cwd: Path,
        sandbox: str,
        writable_roots: list[Path],
        approval_policy: str,
        ephemeral: bool,
    ) -> Result:
        self.connection.request("initialize", {"clientInfo": {
            "name": "seeker",
            "title": "Seeker Codex Proxy",
            "version": "1.0.0",
        }}, 0)
        self.connection.send("initialized", {})

        thread_sandbox = {
            "workspaceWrite": "workspace-write",
            "readOnly": "read-only",
            "dangerFullAccess": "danger-full-access",
        }.get(sandbox, sandbox)
        result = self.connection.request("thread/start", {
            "model": model,
            "cwd": str(cwd),
            "approvalPolicy": approval_policy,
            "sandbox": thread_sandbox,
            "serviceName": "seeker",
            "ephemeral": ephemeral,
        }, 1)
        thread_id = str((result.get("thread") or {}).get("id") or "")
        if not thread_id:
            raise RuntimeError("Codex App Server returned no thread id.")

        policy: dict[str, object] = {"type": sandbox}
        if sandbox == "workspaceWrite":
            policy |= {
                "writableRoots": [str(path) for path in writable_roots],
                "networkAccess": False,
            }
        self.connection.send("turn/start", {
            "threadId": thread_id,
            "input": [{"type": "text", "text": prompt}],
            "cwd": str(cwd),
            "approvalPolicy": approval_policy,
            "sandboxPolicy": policy,
            "model": model,
            "effort": reasoning_effort,
        }, 2)

        usage = {key: 0 for key in USAGE_FIELDS}
        final_message = ""
        while True:
            message = self.connection.receive()
            if message.get("id") == 2:
                if error := message.get("error"):
                    raise RuntimeError(json.dumps(error, ensure_ascii=False))
                continue
            method = message.get("method")
            params = message.get("params") or {}
            if method == "item/completed":
                item = params.get("item") or {}
                if item.get("type") == "agentMessage":
                    final_message = str(item.get("text") or "")
            elif method == "thread/tokenUsage/updated":
                values = (params.get("tokenUsage") or {}).get("last") or {}
                usage = {
                    key: int(values.get(field) or 0)
                    for key, field in USAGE_FIELDS.items()
                }
            elif method == "turn/completed":
                turn = params.get("turn") or {}
                errors = (
                    [json.dumps(turn["error"], ensure_ascii=False)]
                    if turn.get("error") else []
                )
                return Result(
                    ["codex-app-server", self.url],
                    int(turn.get("status") != "completed"),
                    thread_id,
                    final_message,
                    usage,
                    errors,
                )
            elif "id" in message and "method" in message:
                raise RuntimeError(
                    f"Unexpected App Server request: {message.get('method')}"
                )


def run_turn(**arguments: Any) -> Result:
    url = str(arguments.pop("url"))
    client: AppServerClient | None = None
    try:
        client = AppServerClient(url)
        return client.execute(**arguments)
    except Exception as error:
        return Result(
            ["codex-app-server", url],
            1,
            "",
            "",
            {key: 0 for key in USAGE_FIELDS},
            [str(error)],
        )
    finally:
        if client:
            client.close()
