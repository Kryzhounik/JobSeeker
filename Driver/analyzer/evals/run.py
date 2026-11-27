"""Run the production analyzer against an isolated SQL-seeded dataset."""

from __future__ import annotations

import argparse
import configparser
from contextlib import closing
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess
from uuid import uuid4
from zipfile import ZipFile

if __package__:
    from .report import build_report, save_report
else:
    from report import build_report, save_report


ROOT = Path(__file__).resolve().parents[3]
EVAL_ROOT = ROOT / "tmp" / "analyzer-evals"


def copy_tree(source: Path, target: Path) -> None:
    shutil.copytree(source, target, ignore=shutil.ignore_patterns(
        "__pycache__", "*.pyc", "target", "node_modules", "evals"
    ))


def property_file(path: Path) -> dict[str, str]:
    values = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if line and not line.startswith(("#", "!")) and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    return values


def isolated_env(workspace: Path) -> dict[str, str]:
    env = os.environ.copy()
    env.pop("PYTHONHOME", None)
    env["PYTHONPATH"] = os.pathsep.join((str(workspace), str(workspace / "Driver")))
    env["PYTHONNOUSERSITE"] = "1"
    return env


def prepare(dataset_sql: Path, workspace: Path, project_root: Path) -> dict:
    """Reset and prepare a workspace without calling the model."""
    project_root = project_root.resolve()
    workspace = workspace.resolve()
    dataset_sql = dataset_sql.resolve(strict=True)
    if (workspace == project_root or workspace in project_root.parents
            or (project_root in workspace.parents and not workspace.is_relative_to(project_root / "tmp"))
            or dataset_sql.is_relative_to(workspace)):
        raise ValueError("Workspace must not contain the project or dataset or overlap project data")
    if workspace.exists():
        shutil.rmtree(workspace)
    workspace.mkdir(parents=True)

    for name in ("analyzer", "common", "contracts", "db", "codex_proxy"):
        copy_tree(project_root / "Driver" / name, workspace / "Driver" / name)
    for name in ("config", "filtering"):
        copy_tree(project_root / "Driver" / "collector" / name,
                  workspace / "Driver" / "collector" / name)
    copy_tree(project_root / "Driver" / "orchestrator", workspace / "Driver" / "orchestrator")
    copy_tree(project_root / "libs", workspace / "libs")
    (workspace / "Tools").mkdir()
    shutil.copy2(project_root / "Tools" / "application_logging.py", workspace / "Tools")
    shutil.copytree(project_root / "Data" / "Profile", workspace / "Data" / "Profile")
    for required in ("resume.ini", "Driver/analyzer/filter.ini", "Driver/analyzer/interest.ini"):
        if not (workspace / "Data" / "Profile" / required).is_file():
            raise ValueError(f"Missing profile input: {required}")
    filter_path = workspace / "Data" / "Profile" / "Driver" / "analyzer" / "filter.ini"
    filter_config = configparser.ConfigParser()
    filter_config.read(filter_path, encoding="utf-8")
    if not filter_config.has_section("filters"):
        filter_config.add_section("filters")
    for name in ("languages", "programming_languages", "remote", "relocation"):
        filter_config.set("filters", name, "off")
    with filter_path.open("w", encoding="utf-8") as file:
        filter_config.write(file)

    runtime = property_file(project_root / "Tools" / "runtime.properties")
    for key in ("python.home", "python.executable", "python.library", "jpy.library", "jdl.library"):
        if not Path(runtime.get(key, "")).is_absolute() or not Path(runtime[key]).exists():
            raise ValueError(f"Runtime setting must point to an existing absolute path: {key}")
    runtime["database.path"] = "Data/jobs.sqlite"
    (workspace / "Tools" / "runtime.properties").write_text(
        "".join(f"{key}={value.replace(os.sep, '/')}\n" for key, value in runtime.items()), encoding="utf-8"
    )
    collector_config = workspace / "Driver" / "collector" / "config" / "collector.properties"
    lines = collector_config.read_text(encoding="utf-8").splitlines()
    collector_config.write_text("\n".join(
        "raw.directory=Data/raw" if line.strip().startswith("raw.directory=") else line for line in lines
    ) + "\n", encoding="utf-8")
    if property_file(collector_config).get("raw.directory") != "Data/raw":
        raise ValueError("Cannot isolate collector raw.directory")

    database = workspace / "Data" / "jobs.sqlite"
    probe = """
import json, sys
from pathlib import Path
from common.paths import PROJECT_ROOT, DATA_ROOT, PROFILE_ROOT
import orchestrator.processing as processing
from db.migrate import migrate_database
root = Path.cwd().resolve()
assert PROJECT_ROOT.resolve() == root and DATA_ROOT.resolve() == root / 'Data'
assert PROFILE_ROOT.resolve() == root / 'Data' / 'Profile'
assert Path(processing.__file__).resolve().is_relative_to(root / 'Driver')
assert processing.DATA_ROOT.resolve() == root / 'Data'
assert processing.PROFILE_ROOT.resolve() == root / 'Data' / 'Profile'
migrate_database(root / 'Data' / 'jobs.sqlite')
print(json.dumps({'project': str(PROJECT_ROOT), 'processing': processing.__file__, 'profile': str(PROFILE_ROOT)}))
"""
    checked = subprocess.run([runtime["python.executable"], "-c", probe], cwd=workspace,
                             env=isolated_env(workspace), capture_output=True, text=True)
    if checked.returncode:
        raise RuntimeError(f"Workspace Python initialization failed:\n{checked.stderr}")
    with closing(sqlite3.connect(database)) as connection:
        connection.execute("PRAGMA foreign_keys=ON")
        connection.executescript(dataset_sql.read_text(encoding="utf-8"))
        if connection.execute("PRAGMA foreign_key_check").fetchone():
            raise ValueError("Dataset contains foreign key violations")
        rows = connection.execute(
            "SELECT j.collection_run_id, j.source, j.source_job_id, j.processing_status, t.readable_text "
            "FROM source_jobs j LEFT JOIN source_job_texts t ON t.source_job_ref=j.id ORDER BY j.id"
        ).fetchall()
        if not rows or any(not run or status != "RELEVANT" or not text or not text.strip()
                           for run, source, job, status, text in rows):
            raise ValueError("Dataset must contain readable RELEVANT vacancies in a collection run")
        runs = {row[0] for row in rows}
        sources = {row[1] for row in rows}
        ids = [row[2] for row in rows]
        if len(runs) != 1 or len(sources) != 1 or any(not i or ',' in i for i in ids):
            raise ValueError("Dataset must use one run, one source, and comma-free job IDs")
    return {"run_id": rows[0][0], "source": rows[0][1], "job_ids": ids, "database": str(database)}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("dataset", type=Path, help="Path to dataset.sql")
    parser.add_argument("--prepare-only", action="store_true", help="Prepare and validate without model calls")
    parser.add_argument("--java", default="java", help="Java executable")
    parser.add_argument("--k", type=float, default=3.0, help="Underestimation penalty (default: 3)")
    args = parser.parse_args()
    if not math.isfinite(args.k) or args.k <= 0:
        parser.error("--k must be a positive finite number")
    scores = args.dataset.parent / "external-scores.csv"
    if not args.prepare_only and not scores.is_file():
        raise FileNotFoundError(f"Missing reference scores: {scores}")
    workspace = EVAL_ROOT / "workspace"
    prepared = prepare(args.dataset, workspace, ROOT)
    jar = workspace / "Driver" / "orchestrator" / "workflow-orchestrator.jar"
    if not jar.is_file():
        raise FileNotFoundError("Build the Orchestrator before running evaluations")
    with ZipFile(workspace / "libs" / "tools-1.0-SNAPSHOT.jar") as tools_jar:
        if "com/seeker/logging/LoggingAspect.class" not in tools_jar.namelist():
            raise ValueError("Tools JAR is incomplete; rebuild the Orchestrator reactor")
    if args.prepare_only:
        print(json.dumps(prepared, indent=2))
        return

    run_name = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ-") + uuid4().hex[:8]
    result = EVAL_ROOT / "results" / run_name
    result.mkdir(parents=True)
    print(f"Evaluation results: {result}", flush=True)
    shutil.copy2(args.dataset, result / "dataset.sql")
    if scores.is_file():
        shutil.copy2(scores, result / "external-scores.csv")
    shutil.copytree(workspace / "Data" / "Profile", result / "Profile")
    for name in ("analyzer", "contracts"):
        copy_tree(workspace / "Driver" / name, result / "inputs" / "Driver" / name)
    copy_tree(workspace / "Driver" / "common" / "prompts",
              result / "inputs" / "Driver" / "common" / "prompts")
    (result / "inputs" / "Driver" / "codex_proxy").mkdir(parents=True)
    for name in ("config.ini", "transport_instructions.md"):
        shutil.copy2(workspace / "Driver" / "codex_proxy" / name,
                     result / "inputs" / "Driver" / "codex_proxy" / name)
    settings = configparser.ConfigParser()
    settings.read(workspace / "Driver" / "codex_proxy" / "config.ini", encoding="utf-8")
    execution = configparser.ConfigParser()
    execution.read(workspace / "Driver" / "analyzer" / "config" / "execution.ini", encoding="utf-8")
    git = subprocess.run(["git", "rev-parse", "HEAD"], cwd=ROOT, capture_output=True, text=True, check=True)
    status = subprocess.run(["git", "status", "--porcelain"], cwd=ROOT, capture_output=True, text=True, check=True)
    manifest = {**prepared, "dataset_sha256": digest(args.dataset), "git_commit": git.stdout.strip(),
                "git_status": status.stdout, "model": settings.get("proxy", "model"),
                "reasoning_effort": settings.get("proxy", "reasoning_effort"),
                "execution": dict(execution["job_facts"]),
                "files_sha256": {str(p.relative_to(workspace)): digest(p) for base in
                    (workspace / "Driver", workspace / "libs") for p in base.rglob("*") if p.is_file()}}
    (result / "metadata.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    try:
        for command in ("job-facts", "finish-analysis"):
            with (result / f"{command}.stdout.log").open("w", encoding="utf-8") as stdout, \
                 (result / f"{command}.stderr.log").open("w", encoding="utf-8") as stderr:
                subprocess.run([args.java, "-jar", str(jar), command, prepared["run_id"], prepared["source"],
                                ",".join(prepared["job_ids"])], cwd=workspace, env=isolated_env(workspace),
                               stdout=stdout, stderr=stderr, check=True)
    finally:
        shutil.copytree(workspace / "Data", result / "Data")
    report = build_report(result, args.k)
    save_report(result, report)
    print(f"Evaluation reports: {result / 'report.csv'}, {result / 'report.json'}", flush=True)
    if not report['complete']:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
