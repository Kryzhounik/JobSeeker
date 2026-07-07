"""Compatibility entry point.

The real post-analysis workflow lives in workflow/save_analyzed_job.py. Keep
this thin wrapper only so older commands do not break.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from workflow.save_analyzed_job import main


if __name__ == "__main__":
    main()
