"""Compatibility import for the old common job filter path.

The real candidate-fit filter now lives in scoring/candidate_fit/filter.py.
Do not add new logic here.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scoring.candidate_fit.filter import *  # noqa: F401,F403
