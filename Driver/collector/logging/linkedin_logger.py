"""Compatibility import and CLI for the shared collection logger."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[2]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from collector.logging.collection_logger import (
    DEFAULT_DB, apply_schema, collection_event_from_payload, integer, load_json_input,
    main, preview_from_payload, record_collection_event, record_preview_filter, text,
)

if __name__ == "__main__":
    main()
