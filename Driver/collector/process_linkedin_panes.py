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
    )


def load_list(path: Path) -> list[dict[str, Any]]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, list) or not all(isinstance(item, dict) for item in value):
        raise ValueError(f"Expected a JSON object list: {path}")
    return value


def write_scope(path: Path, scope: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(scope, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def process(batch_path: Path, scope_path: Path) -> list[dict[str, str]]:
    batch = load_list(batch_path)
    scope = load_list(scope_path) if scope_path.exists() else []
    scoped_ids = {str(item.get("job_id", "")) for item in scope}
    outcomes: list[dict[str, str]] = []

    for item in batch:
        preview = item.get("preview")
        if not isinstance(preview, dict):
            raise ValueError("Browser result is missing preview")

        job_id = str(preview.get("job_id", "")).strip()
        source_url = str(preview.get("source_url", "")).strip()
        title = str(preview.get("title", "")).strip()
        company = str(preview.get("company", "")).strip()
        if not job_id or not source_url:
            raise ValueError("Browser result is missing job_id or source_url")

        status = str(item.get("status", ""))
        if status != "pane_saved":
            outcomes.append({"job_id": job_id, "status": status})
            continue

        pane_path = Path(str(item.get("temp_path", "")))
        if not pane_path.is_file():
            raise FileNotFoundError(pane_path)

        run(
            "collector/save_raw_page.py",
            "--source", "linkedin",
            "--url", source_url,
            "--content-file", str(pane_path),
        )
        run(
            "collector/extract_linkedin_readable_text_v2.py",
            "--source", "linkedin",
            "--input", str(DATA_ROOT / "raw" / "linkedin" / "pages" / f"{job_id}.html"),
        )

        decision_path = batch_path.parent / f"content_{job_id}.json"
        run(
            "collector/filtering/linkedin_filter.py",
            "content",
            "--source", "linkedin",
            "--job-id", job_id,
            "--title", title,
            "--output", str(decision_path),
        )
        decision = json.loads(decision_path.read_text(encoding="utf-8"))
        analyze = decision["content_decision"] == "analyze"
        final_status = "raw_saved" if analyze else "content_filtered"

        run(
            "collector/logging/linkedin_logger.py",
            "collection",
            "--label", str(item.get("label", "")),
            "--start", str(item.get("start", "")),
            "--card-index", str(item.get("index", "")),
            "--job-id", job_id,
            "--source-url", source_url,
            "--title", title,
            "--company", company,
            "--status", final_status,
            "--reason", str(decision.get("content_reason", "")),
        )

        if analyze and job_id not in scoped_ids:
            scope.append({"source": "linkedin", "job_id": job_id, "title": title})
            scoped_ids.add(job_id)
            write_scope(scope_path, scope)

        pane_path.unlink()
        outcomes.append({"job_id": job_id, "status": final_status})

    return outcomes


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Process saved LinkedIn panes through deterministic collector stages."
    )
    parser.add_argument("--batch", type=Path, required=True)
    parser.add_argument("--scope", type=Path, required=True)
    args = parser.parse_args()
    print(json.dumps(process(args.batch, args.scope), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
