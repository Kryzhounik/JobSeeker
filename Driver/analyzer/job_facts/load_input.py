"""Run the documented readable/language input sequence without shell payloads."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import subprocess
import sys


DRIVER_ROOT = Path(__file__).resolve().parents[2]


def load_input(source: str, job_id: str) -> dict[str, object]:
    readable = subprocess.run(
        [sys.executable, "db/readable_text.py", "--source", source, "--job-id", job_id],
        cwd=DRIVER_ROOT,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    language_facts = subprocess.run(
        [sys.executable, "collector/filtering/language_requirements.py"],
        cwd=DRIVER_ROOT,
        input=readable,
        check=True,
        stdout=subprocess.PIPE,
    ).stdout
    return {
        "source": source,
        "job_id": job_id,
        "readable_text": readable.decode("utf-8"),
        "authoritative_language_facts": json.loads(language_facts.decode("utf-8")),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--job-id", required=True)
    args = parser.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    try:
        result = load_input(args.source, args.job_id)
    except subprocess.CalledProcessError as error:
        raise SystemExit(error.returncode) from error
    print(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
