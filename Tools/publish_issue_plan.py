"""Publish an agreed GitHub Issue plan using an authenticated `gh` on PATH.

Run from the repository root:
    python Tools/publish_issue_plan.py OWNER/REPO ISSUE PLAN_FILE [DRAFT_FILE]
Example:
    python Tools/publish_issue_plan.py Kryzhounik/JobSeeker 4 tmp/plan.md tmp/draft.md

PLAN_FILE contains the final plan. DRAFT_FILE contains the exact fragment to
remove from the issue description, including its heading and line breaks.
Use UTF-8 files. No trimming, Markdown parsing, or guessed deletion boundaries.
Omit DRAFT_FILE if there is no draft in the description (e.g. it is in comments).

Creates a `plan` artifact, or updates the existing one. Multiple Plan artifacts
or a draft fragment occurring other than exactly once cause an error before any
writes. After publishing, removes only that fragment; comments are never edited.
If the description changes during publication, it is not overwritten. The
published artifact remains if the subsequent description update fails.
"""

import argparse
import json
from pathlib import Path
import subprocess


def api(method, endpoint, payload=None):
    command = ["gh", "api", "--method", method, endpoint]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(
        command, input=None if payload is None else json.dumps(payload),
        stdout=subprocess.PIPE, encoding="utf-8", check=True,
    )
    return json.loads(result.stdout)


def read_text(path):
    # Preserve line breaks so the draft match really is exact.
    return Path(path).read_bytes().decode("utf-8-sig")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("repo", help="OWNER/REPO")
    parser.add_argument("issue", type=int)
    parser.add_argument("plan_file")
    parser.add_argument("draft_file", nargs="?")
    args = parser.parse_args()

    plan = read_text(args.plan_file)
    if not plan.strip():
        raise SystemExit("The final plan is empty.")
    draft = read_text(args.draft_file) if args.draft_file else None
    issue_path = f"repos/{args.repo}/issues/{args.issue}"
    body = api("GET", issue_path)["body"] or ""
    if draft is not None and (not draft.strip() or body.count(draft) != 1):
        raise SystemExit("The draft must be nonempty and occur exactly once. Nothing changed.")

    artifacts_path = f"{issue_path}/artifacts"
    plans = api("GET", f"{artifacts_path}?type=plan&per_page=2")
    if len(plans) > 1:
        raise SystemExit("Multiple Plan artifacts found. Nothing changed.")
    if plans:
        artifact = api("PATCH", f"{artifacts_path}/{plans[0]['number']}", {"body": plan})
    else:
        artifact = api("POST", artifacts_path,
                       {"type": "plan", "name": "Implementation plan", "body": plan})
    print(f"Plan: https://github.com/{args.repo}/issues/{args.issue}/artifacts/{artifact['number']}", flush=True)

    if draft is not None:
        if (api("GET", issue_path)["body"] or "") != body:
            raise SystemExit("Description changed during publication. Plan published; description untouched.")
        api("PATCH", issue_path, {"body": body.replace(draft, "", 1)})
        print("Draft removed; other description text unchanged.")


if __name__ == "__main__":
    main()
