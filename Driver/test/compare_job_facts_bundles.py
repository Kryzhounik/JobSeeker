"""Compare two Desktop job-facts bundles with one CLI bundle."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import sys
from typing import Any


DRIVER_ROOT = Path(__file__).resolve().parents[1]
if str(DRIVER_ROOT) not in sys.path:
    sys.path.insert(0, str(DRIVER_ROOT))

from codex_proxy.comparison import save_comparison
from common.paths import DATA_ROOT
from contracts.validate_json import validate_json


SCALAR_FIELDS = (
    "source",
    "added_at",
    "source_url",
    "title",
    "company",
    "location",
    "remote_type",
    "remote_scope",
    "relocation",
    "seniority",
    "role",
    "salary",
)

CONCEPT_PATTERNS = {
    "java": r"\bjava\b",
    "kotlin": r"\bkotlin\b",
    "spring": r"\bspring(?: boot| framework)?\b",
    "hibernate/jpa": r"\b(?:hibernate|jpa)\b",
    "vue.js": r"\bvue(?:\.js)?\b",
    "react": r"\breact\b",
    "kafka": r"\bkafka\b",
    "postgresql": r"\b(?:postgres|postgresql)\b",
    "mongodb": r"\bmongodb\b",
    "oracle db": r"\boracle(?: db)?\b",
    "vertica": r"\bvertica\b",
    "druid": r"\bdruid\b",
    "sql": r"(?<!no)\bsql\b",
    "nosql": r"\bnosql\b",
    "gcp": r"\b(?:google cloud platform|gcp)\b",
    "aws": r"\baws\b",
    "azure": r"\bazure\b",
    "kubernetes": r"\bkubernetes\b",
    "docker": r"\bdocker\b",
    "rest api": r"\brest(?:ful)? apis?\b",
    "grpc": r"\bgrpc\b",
    "grafana": r"\bgrafana\b",
    "opentelemetry": r"\bopentelemetry\b",
    "playwright": r"\bplaywright\b",
    "flowable": r"\bflowable\b",
    "openl": r"\bopenl\b",
    "rabbitmq": r"\brabbitmq\b",
    "elasticsearch": r"\belasticsearch\b",
    "ci/cd": r"\bci\s*/\s*cd\b",
    "domain-driven design": r"\b(?:domain-driven design|ddd)\b",
    "microservices": r"\bmicroservices?\b",
    "distributed systems": r"\bdistributed (?:systems?|software applications?)\b",
    "containerization": r"\bcontainerization\b",
    "cloud platforms": r"\bcloud platforms?\b",
    "cloud architecture": r"\bcloud architecture\b",
    "high-throughput data processing": r"\bhigh-throughput data processing\b",
    "test automation": r"\b(?:test-driven development|test automation)\b",
    "unit/integration testing": r"\bunit and integration testing\b",
    "ai-assisted development": r"\bai-assisted software development(?: tools)?\b",
    "agentic ai": r"\bagentic ai\b",
    "event-driven architecture": r"\bevent-driven architecture\b",
    "it security": r"\bit security\b",
    "technical leadership": r"\b(?:cross-team )?technical leadership\b",
    "software architecture": r"\bsoftware architecture\b",
    "system design": r"\bsystem design\b",
    "software engineering": r"\b(?:large-scale )?software engineering\b",
    "engineering processes at scale": r"\bengineering processes at scale\b",
    "observability": r"\b(?:service monitoring and observability|monitoring)\b",
    "solid/oop": r"\b(?:solid|object-oriented programming)\b",
}


def load_bundle(path: Path) -> dict[str, dict[str, Any]]:
    if path.is_dir():
        bundle: dict[str, dict[str, Any]] = {}
        for item_path in sorted(path.glob("*.json")):
            response = json.loads(item_path.read_text(encoding="utf-8"))
            validate_json(
                response,
                DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
            )
            job_id = item_path.stem.split("-", 1)[-1]
            bundle[job_id] = response
        if not bundle:
            raise ValueError(f"{path}: no JSON responses found")
        return bundle
    payload = json.loads(path.read_text(encoding="utf-8"))
    results = payload.get("results")
    if not isinstance(results, list):
        raise ValueError(f"{path}: results must be an array")
    bundle: dict[str, dict[str, Any]] = {}
    for item in results:
        job_id = str(item["job_id"])
        response = item["response"]
        validate_json(
            response,
            DRIVER_ROOT / "contracts" / "job_analysis.schema.json",
        )
        bundle[job_id] = response
    return bundle


def normalized_name(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", " ", value.casefold()).strip()


def technology_concepts(entry: dict[str, Any]) -> set[str]:
    name = entry["name"].casefold()
    concepts = {
        concept
        for concept, pattern in CONCEPT_PATTERNS.items()
        if re.search(pattern, name)
    }
    return concepts or {normalized_name(entry["name"])}


def technology_map(response: dict[str, Any]) -> dict[str, set[tuple[str, int]]]:
    result: dict[str, set[tuple[str, int]]] = {}
    for entry in response["technologies"]:
        attributes = (entry["requirement"], entry["level_rank"])
        for concept in technology_concepts(entry):
            result.setdefault(concept, set()).add(attributes)
    return result


def language_set(response: dict[str, Any]) -> set[tuple[str, str, int]]:
    return {
        (entry["name"].casefold(), entry["level"], entry["level_rank"])
        for entry in response["languages"]
    }


def compare_pair(
    left: dict[str, Any],
    right: dict[str, Any],
) -> dict[str, Any]:
    scalar_differences = [
        field for field in SCALAR_FIELDS if left[field] != right[field]
    ]
    left_languages = language_set(left)
    right_languages = language_set(right)
    language_differences = sorted(left_languages ^ right_languages)

    left_technologies = technology_map(left)
    right_technologies = technology_map(right)
    left_concepts = set(left_technologies)
    right_concepts = set(right_technologies)
    only_left = sorted(left_concepts - right_concepts)
    only_right = sorted(right_concepts - left_concepts)
    attribute_differences = sorted(
        concept
        for concept in left_concepts & right_concepts
        if left_technologies[concept] != right_technologies[concept]
    )
    union = left_concepts | right_concepts
    jaccard = 1.0 if not union else len(left_concepts & right_concepts) / len(union)
    distance = (
        len(scalar_differences)
        + len(language_differences)
        + len(only_left)
        + len(only_right)
        + len(attribute_differences)
    )
    return {
        "distance": distance,
        "scalar_differences": scalar_differences,
        "language_differences": language_differences,
        "technology_jaccard": round(jaccard, 4),
        "technology_only_left": only_left,
        "technology_only_right": only_right,
        "technology_attribute_differences": attribute_differences,
        "summary_text_equal": left["summary"] == right["summary"],
        "notes_text_equal": left["notes"] == right["notes"],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--desktop-a", type=Path, required=True)
    parser.add_argument("--desktop-b", type=Path, required=True)
    parser.add_argument("--cli", type=Path, required=True)
    parser.add_argument("--operation-prefix", required=True)
    parser.add_argument("--db", type=Path, default=DATA_ROOT / "jobs.sqlite")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    desktop_a = load_bundle(args.desktop_a)
    desktop_b = load_bundle(args.desktop_b)
    cli = load_bundle(args.cli)
    if set(desktop_a) != set(desktop_b) or set(desktop_a) != set(cli):
        raise ValueError("all bundles must contain the same job IDs")

    jobs: dict[str, Any] = {}
    for job_id in desktop_a:
        a_to_b = compare_pair(desktop_a[job_id], desktop_b[job_id])
        a_to_cli = compare_pair(desktop_a[job_id], cli[job_id])
        b_to_cli = compare_pair(desktop_b[job_id], cli[job_id])
        cli_nearest_distance = min(a_to_cli["distance"], b_to_cli["distance"])
        jobs[job_id] = {
            "desktop_a_to_b": a_to_b,
            "desktop_a_to_cli": a_to_cli,
            "desktop_b_to_cli": b_to_cli,
            "cli_nearest_distance": cli_nearest_distance,
            "within_desktop_spread": cli_nearest_distance <= a_to_b["distance"],
        }
        save_comparison(
            db_path=args.db,
            operation_id=f"{args.operation_prefix}:{job_id}:desktop-a",
            operation_type="job_facts",
            desktop_response=desktop_a[job_id],
            cli_response=cli[job_id],
        )
        save_comparison(
            db_path=args.db,
            operation_id=f"{args.operation_prefix}:{job_id}:desktop-b",
            operation_type="job_facts",
            desktop_response=desktop_b[job_id],
            cli_response=cli[job_id],
        )

    baseline_total = sum(
        item["desktop_a_to_b"]["distance"] for item in jobs.values()
    )
    cli_nearest_total = sum(
        item["cli_nearest_distance"] for item in jobs.values()
    )
    report = {
        "operation_prefix": args.operation_prefix,
        "method": (
            "Distance counts categorical, language, technology-concept presence, "
            "and technology requirement/level disagreements; wording differences in "
            "summary and notes are reported but excluded. CLI is compared with its "
            "nearest independent Desktop result."
        ),
        "baseline_desktop_distance_total": baseline_total,
        "cli_nearest_distance_total": cli_nearest_total,
        "within_desktop_spread_overall": cli_nearest_total <= baseline_total,
        "jobs_within_desktop_spread": sum(
            bool(item["within_desktop_spread"]) for item in jobs.values()
        ),
        "job_count": len(jobs),
        "jobs": jobs,
    }
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output is not None:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    print(rendered, end="")


if __name__ == "__main__":
    main()
