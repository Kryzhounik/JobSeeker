"""Shared project paths.

Driver code should use these constants instead of hard-coded top-level folder
names. This keeps the current folder layout change mechanical and explicit.
"""

from __future__ import annotations

from pathlib import Path


DRIVER_ROOT = Path(__file__).resolve().parents[1]
PROJECT_ROOT = DRIVER_ROOT.parent if DRIVER_ROOT.name == "Driver" else DRIVER_ROOT
DATA_ROOT = PROJECT_ROOT / "Data"
GUI_ROOT = PROJECT_ROOT / "GUI"
PROJECT_META_ROOT = PROJECT_ROOT
