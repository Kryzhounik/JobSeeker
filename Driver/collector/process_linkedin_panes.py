from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DATA_ROOT = ROOT.parent / "Data"


def run(*args: str) -> None:
    subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        check=True,
        text=True,
        encoding="utf-8",
        stdout=subprocess.DEVNULL,
    )


def run_json(*args: str) -> dict[str, Any]:
    completed = subprocess.run(
        [sys.executable, *args],
        cwd=ROOT,
        check=True,
        text=True,
        encoding="utf-8",
        stdout=subprocess.PIPE,
    )
    value = json.loads(completed.stdout)
    if not isinstance(value, dict):
        raise ValueError("Collector command returned a non-object JSON result")
    return value


def process(
    job_id: str,
    source_url: str,
    title: str,
    company: str,
    pane_path: Path,
    label: str = "",
    start: str = "",
    index: str = "",
    db_path: Path = DATA_ROOT / "jobs.sqlite",
    raw_dir: Path = DATA_ROOT / "raw" / "linkedin",
) -> dict[str, str]:
    job_id = job_id.strip()
    source_url = source_url.strip()
    pane_path = pane_path.resolve()
    db_path = db_path.resolve()
    raw_dir = raw_dir.resolve()
    if not job_id or not source_url:
        raise ValueError("Browser result is missing job_id or source_url")
    if not pane_path.is_file():
        raise FileNotFoundError(pane_path)

    run(
        "collector/save_raw_page.py",
        "--source", "linkedin",
        "--url", source_url,
        "--content-file", str(pane_path),
        "--out-dir", str(raw_dir),
        "--db", str(db_path),
        "--collection-method", "browser",
    )
    run(
        "collector/extract_linkedin_readable_text_v2.py",
        "--source", "linkedin",
        "--input", str(raw_dir / "pages" / f"{job_id}.html"),
        "--db", str(db_path),
    )

    decision = run_json(
        "collector/filtering/linkedin_filter.py",
        "content",
        "--source", "linkedin",
        "--job-id", job_id,
        "--title", title,
        "--db", str(db_path),
    )
    analyze = decision["content_decision"] == "analyze"
    final_status = "raw_saved" if analyze else "content_filtered"
    reason = str(decision.get("content_reason", ""))

    run(
        "collector/logging/linkedin_logger.py",
        "collection",
        "--label", label,
        "--start", start,
        "--card-index", index,
        "--job-id", job_id,
        "--source-url", source_url,
        "--title", title,
        "--company", company,
        "--status", final_status,
        "--reason", reason,
        "--db", str(db_path),
    )

    pane_path.unlink()
    return {
        "source": "linkedin",
        "job_id": job_id,
        "title": title,
        "status": final_status,
        "reason": reason,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Process saved LinkedIn panes through deterministic collector stages."
    )
    parser.add_argument("--job-id", required=True)
    parser.add_argument("--source-url", required=True)
    parser.add_argument("--title", required=True)
    parser.add_argument("--company", default="")
    parser.add_argument("--pane-html", type=Path, required=True)
    parser.add_argument("--label", default="")
    parser.add_argument("--start", default="")
    parser.add_argument("--index", default="")
    parser.add_argument("--db", type=Path, default=DATA_ROOT / "jobs.sqlite")
    parser.add_argument(
        "--raw-dir",
        type=Path,
        default=DATA_ROOT / "raw" / "linkedin",
    )
    args = parser.parse_args()
    print(
        json.dumps(
            process(
                args.job_id,
                args.source_url,
                args.title,
                args.company,
                args.pane_html,
                args.label,
                args.start,
                args.index,
                args.db,
                args.raw_dir,
            ),
            ensure_ascii=False,
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
