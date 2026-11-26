"""Process logging setup shared by Python entry points and the embedded JVM."""

from __future__ import annotations

import copy
from datetime import datetime, timezone
import logging
from logging.handlers import RotatingFileHandler
import os
from pathlib import Path
import sys
import time


_configuration = None
_process = f"python-{os.getpid()}-{time.time_ns()}"


class _Formatter(logging.Formatter):
    def formatTime(self, record, datefmt=None):
        return datetime.fromtimestamp(record.created, timezone.utc).isoformat(
            timespec="milliseconds"
        ).replace("+00:00", "Z")

    def format(self, record):
        record = copy.copy(record)
        record.levelname = {"WARNING": "WARN", "CRITICAL": "ERROR"}.get(
            record.levelname, record.levelname
        )
        return super().format(record)


class _EmbeddedHandler(logging.Handler):
    def __init__(self, bridge):
        super().__init__()
        self.bridge = bridge

    def emit(self, record):
        exception = record.exc_text or ""
        if record.exc_info:
            exception = logging.Formatter().formatException(record.exc_info)
        if record.stack_info:
            exception = "\n".join(filter(None, (exception, record.stack_info)))
        self.bridge.log(record.name, record.levelno, record.getMessage(), exception)


def initialize_embedded(bridge) -> None:
    """Install the JVM-owned sink without modifying Python's result streams."""
    global _configuration
    if _configuration == ("embedded",):
        return
    logging.basicConfig(level=logging.NOTSET, handlers=[_EmbeddedHandler(bridge)], force=True)
    _configuration = ("embedded",)


def initialize_standalone(project_root: Path) -> None:
    """Configure standard Python handlers; this path never imports JPy."""
    global _configuration
    root = Path(project_root).resolve()
    if _configuration == ("standalone", root):
        return
    directory = root / "Data" / "logs"
    directory.mkdir(parents=True, exist_ok=True)
    console = logging.StreamHandler(sys.stderr)
    file = RotatingFileHandler(directory / f"{_process}.log", maxBytes=10 * 1024 * 1024,
                               backupCount=7, encoding="utf-8")
    formatter = _Formatter("%(asctime)s %(levelname)-5s %(name)s - %(message)s")
    console.setFormatter(formatter)
    file.setFormatter(formatter)
    logging.basicConfig(level=logging.INFO, handlers=[console, file], force=True)
    _configuration = ("standalone", root)
