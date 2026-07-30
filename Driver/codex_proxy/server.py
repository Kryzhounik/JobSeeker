"""Lifecycle commands for Seeker's shared Codex App Server."""

from __future__ import annotations

import argparse
from configparser import ConfigParser
import json
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import time
from urllib.parse import urlparse


CONFIG_PATH = Path(__file__).with_name("config.ini")
STATE_PATH = Path(tempfile.gettempdir()) / "seeker-codex-app-server.json"
LOG_PATH = Path(tempfile.gettempdir()) / "seeker-codex-app-server.log"


def hidden_process_flags() -> int:
    if os.name != "nt":
        return 0
    return (
        subprocess.CREATE_NEW_PROCESS_GROUP
        | subprocess.CREATE_NO_WINDOW
    )


def server_url() -> str:
    config = ConfigParser()
    if not config.read(CONFIG_PATH, encoding="utf-8"):
        raise SystemExit(f"Missing Codex proxy config: {CONFIG_PATH}")
    return config.get("proxy", "app_server_url")


def port_is_open(url: str) -> bool:
    parsed = urlparse(url)
    if not parsed.hostname or not parsed.port:
        raise SystemExit(f"Invalid app_server_url: {url}")
    try:
        with socket.create_connection((parsed.hostname, parsed.port), timeout=1):
            return True
    except OSError:
        return False


def codex_executable() -> str:
    launcher = shutil.which("codex.cmd") or shutil.which("codex")
    if not launcher:
        raise SystemExit("Codex CLI was not found on PATH.")
    if os.name != "nt":
        return launcher
    package_root = (
        Path(launcher).parent
        / "node_modules"
        / "@openai"
        / "codex"
        / "node_modules"
        / "@openai"
    )
    candidates = list(package_root.glob(
        "codex-win32-*/vendor/*/bin/codex.exe"
    ))
    if len(candidates) != 1:
        raise SystemExit(f"Cannot locate native Codex under {package_root}.")
    return str(candidates[0])


def start(*, quiet: bool = False) -> bool:
    url = server_url()
    if STATE_PATH.exists():
        if port_is_open(url):
            if not quiet:
                print("Codex App Server already running.")
            return False
        STATE_PATH.unlink()
    if port_is_open(url):
        raise SystemExit(f"App Server port is already in use: {url}")

    with LOG_PATH.open("w", encoding="utf-8") as log:
        process = subprocess.Popen(
            [codex_executable(), "app-server", "--listen", url],
            stdin=subprocess.DEVNULL,
            stdout=log,
            stderr=subprocess.STDOUT,
            creationflags=hidden_process_flags(),
        )
    for _ in range(100):
        if process.poll() is not None:
            raise SystemExit(f"App Server failed. See {LOG_PATH}")
        if port_is_open(url):
            STATE_PATH.write_text(
                json.dumps({"pid": process.pid, "url": url}),
                encoding="utf-8",
            )
            if not quiet:
                print(f"Codex App Server started: {process.pid}")
            return True
        time.sleep(0.1)
    raise SystemExit(f"App Server did not become ready. See {LOG_PATH}")


def stop(*, quiet: bool = False) -> None:
    if not STATE_PATH.exists():
        if not quiet:
            print("Codex App Server is not running.")
        return
    state = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    pid = int(state["pid"])
    if port_is_open(str(state["url"])):
        if os.name == "nt":
            command = (
                f"Stop-Process -Id {pid} -Force -ErrorAction Stop"
            )
            result = subprocess.run(
                ["powershell.exe", "-NoProfile", "-NonInteractive",
                 "-Command", command],
                capture_output=True,
                text=True,
                check=False,
                creationflags=hidden_process_flags(),
            )
            if result.returncode:
                raise SystemExit(result.stderr.strip())
        else:
            os.kill(pid, 15)
    STATE_PATH.unlink(missing_ok=True)
    if not quiet:
        print("Codex App Server stopped.")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("start", "stop"))
    action = parser.parse_args().action
    start() if action == "start" else stop()


if __name__ == "__main__":
    main()
