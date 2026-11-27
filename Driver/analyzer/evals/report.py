"""Calculate Candidate Fit evaluation metrics from a saved experiment."""

from __future__ import annotations

import argparse
import csv
from contextlib import closing
import json
import math
from pathlib import Path
import sqlite3
from statistics import fmean


def build_report(result_dir: Path, k: float) -> dict:
    if not math.isfinite(k) or k <= 0:
        raise ValueError('Underestimation penalty must be a positive finite number')
    metadata = json.loads((result_dir / 'metadata.json').read_text(encoding='utf-8'))
    scope = [(metadata['source'], str(job_id)) for job_id in metadata['job_ids']]
    if not scope or len(scope) != len(set(scope)):
        raise ValueError('Experiment scope must be nonempty and contain unique identities')
    references = {}
    with (result_dir / 'external-scores.csv').open(encoding='utf-8', newline='') as file:
        for row in csv.DictReader(file):
            key = (row['source'], row['job_id'])
            low, high = float(row['minimum_percent']), float(row['maximum_percent'])
            if key in references or not math.isfinite(low) or low != high or not 0 <= low <= 100:
                raise ValueError(f'Invalid or non-point reference score: {key}')
            references[key] = low
    database = (result_dir / 'Data' / 'jobs.sqlite').resolve(strict=True)
    with closing(sqlite3.connect(database.as_uri() + '?mode=ro', uri=True)) as connection:
        actuals = {(source, job_id): (status, score) for source, job_id, status, score in connection.execute(
            'SELECT s.source, s.source_job_id, s.processing_status, j.candidate_fit_percent '
            'FROM source_jobs s LEFT JOIN jobs j ON j.source_job_ref = s.id'
        )}
    vacancies = []
    for source, job_id in scope:
        expected = references.get((source, job_id))
        status, actual = actuals.get((source, job_id), ('missing', None))
        if status != 'SAVED' or actual is None:
            actual = None
        if actual is not None and (not isinstance(actual, (int, float)) or not 0 <= actual <= 100):
            raise ValueError(f'Invalid Candidate Fit score: {source}:{job_id}')
        error = actual - expected if actual is not None and expected is not None else None
        vacancies.append({'source': source, 'job_id': job_id, 'expected': expected,
                          'actual': actual, 'status': status, 'error': error,
                          'asymmetric_loss': (k if error < 0 else 1) * error ** 2
                          if error is not None else None})
    extra_references = [{'source': source, 'job_id': job_id}
                        for source, job_id in sorted(references.keys() - set(scope))]
    complete = not extra_references and all(row['error'] is not None for row in vacancies)
    metrics = {'asymmetric_mse': None, 'mse': None, 'bias': None}
    if complete:
        metrics = {'asymmetric_mse': fmean(row['asymmetric_loss'] for row in vacancies),
                   'mse': fmean(row['error'] ** 2 for row in vacancies),
                   'bias': fmean(row['error'] for row in vacancies)}
    return {'complete': complete, 'underestimation_penalty': k, 'metrics': metrics,
            'vacancies': vacancies, 'extra_references': extra_references}


def save_report(result_dir: Path, report: dict) -> None:
    (result_dir / 'report.json').write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8'
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('result_dir', type=Path, help='Saved evaluation result directory')
    parser.add_argument('--k', type=float, default=3.0, help='Underestimation penalty (default: 3)')
    args = parser.parse_args()
    report = build_report(args.result_dir, args.k)
    save_report(args.result_dir, report)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if not report['complete']:
        raise SystemExit(1)


if __name__ == '__main__':
    main()
