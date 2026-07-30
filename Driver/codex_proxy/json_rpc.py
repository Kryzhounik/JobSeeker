"""Small JSON-RPC adapter over websocket-client."""

from __future__ import annotations

import json
from typing import Any

from websocket import create_connection


class JsonRpcConnection:
    def __init__(self, url: str) -> None:
        self.socket = create_connection(
            url,
            timeout=10,
            http_no_proxy=["127.0.0.1", "localhost"],
            suppress_origin=True,
        )
        self.socket.settimeout(None)

    def close(self) -> None:
        self.socket.close()

    def send(
        self,
        method: str,
        params: dict[str, object],
        request_id: int | None = None,
    ) -> None:
        message: dict[str, object] = {"method": method, "params": params}
        if request_id is not None:
            message["id"] = request_id
        self.socket.send(json.dumps(message, ensure_ascii=False))

    def receive(self) -> dict[str, Any]:
        raw = self.socket.recv()
        if not isinstance(raw, str):
            raise RuntimeError("App Server returned a non-text message.")
        return json.loads(raw)

    def request(
        self, method: str, params: dict[str, object], request_id: int
    ) -> dict[str, Any]:
        self.send(method, params, request_id)
        while True:
            message = self.receive()
            if message.get("id") == request_id:
                if error := message.get("error"):
                    raise RuntimeError(json.dumps(error, ensure_ascii=False))
                return message.get("result") or {}
