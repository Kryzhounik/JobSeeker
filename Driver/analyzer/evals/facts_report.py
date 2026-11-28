"""Compare expected facts with analyzed JSON from an existing run."""

from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path

if __package__:
    from .report import save_report
else:
    from report import save_report

def language_items(values: list, keys: set[str]) -> Counter:
    return Counter(tuple((key, json.dumps(item[key], sort_keys=True, ensure_ascii=False))
                         for key in sorted(keys) if key in item) for item in values)


def compare_facts(expected_root: Path, analyzed_root: Path) -> dict:
    files = sorted(expected_root.glob('*/*.json'))
    if not files:
        raise ValueError(f'No expected facts found in {expected_root}')
    vacancies = []
    for path in files:
        expected = json.loads(path.read_text(encoding='utf-8'))
        if not isinstance(expected, dict) or not expected:
            raise ValueError(f'Expected facts must be a nonempty object: {path}')
        actual_path = analyzed_root / path.relative_to(expected_root)
        actual = json.loads(actual_path.read_text(encoding='utf-8')) if actual_path.is_file() else None
        if actual is not None and not isinstance(actual, dict):
            raise ValueError(f'Analyzed facts must be an object: {actual_path}')
        fields = {}
        for name, expected_value in expected.items():
            present = actual is not None and name in actual
            actual_value = actual[name] if present else None
            if name == 'languages' and present:
                keys = {key for item in expected_value for key in item}
                match = language_items(expected_value, keys) == language_items(actual_value, keys)
            else:
                match = present and expected_value == actual_value
            fields[name] = {'expected': expected_value, 'actual': actual_value,
                            'present': present, 'match': match}
        vacancies.append({'source': path.parent.name, 'job_id': path.stem,
                          'company': actual.get('company') if actual else None,
                          'title': actual.get('title') if actual else None,
                          'complete': actual is not None and all(f['present'] for f in fields.values()),
                          'fields': fields})
    total = sum(len(row['fields']) for row in vacancies)
    matched = sum(field['match'] for row in vacancies for field in row['fields'].values())
    return {'complete': all(row['complete'] for row in vacancies),
            'matched_fields': matched, 'total_fields': total, 'vacancies': vacancies}


def save_facts_report(result_dir: Path, report: dict) -> None:
    result_dir.mkdir(parents=True, exist_ok=True)
    (result_dir / 'facts-report.json').write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--expected', required=True, type=Path, help='Expected facts directory')
    parser.add_argument('--analyzed', required=True, type=Path, help='Analyzed JSON directory')
    parser.add_argument('--output', required=True, type=Path, help='Directory for reports')
    parser.add_argument('--score-report', type=Path, help='Saved Candidate Fit report.json for combined CSV')
    args = parser.parse_args()
    report = compare_facts(args.expected, args.analyzed)
    save_facts_report(args.output, report)
    if args.score_report:
        scores = json.loads(args.score_report.read_text(encoding='utf-8'))
        save_report(args.output, scores, report)
    print(f"Matched fields: {report['matched_fields']}/{report['total_fields']}; complete: {report['complete']}")
    if not report['complete']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
