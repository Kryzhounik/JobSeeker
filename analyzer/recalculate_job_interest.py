"""CLI entry point for recalculating job interest and candidate fit.

The real implementation lives in scoring/job_interest/calculate.py.
Keep this wrapper thin; scoring logic belongs in scoring/.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from scoring.job_interest.calculate import *  # noqa: F401,F403
from scoring.job_interest.calculate import main


if __name__ == "__main__":
    main()
