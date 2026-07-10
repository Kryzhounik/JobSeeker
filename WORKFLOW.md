# JobSeeker Workflow

Purpose: top-level run order only.

This file does not define internals of collector, analyzer, scoring, or db.
Each stage owns its own rules in its own files.

## Main Pipeline

```text
collector
-> raw HTML
-> analyzer/extract_readable_text_v2.py
-> readable text
-> analyzer/prompts/analyze_job.md
-> analyzed JSON
-> scoring/candidate_fit/evaluate.md
-> candidate-fit-scored JSON
-> scoring/job_interest
-> fully scored JSON
-> workflow/save_analyzed_job.py
-> SQLite
```

## Public Commands

```text
batch <source>
from-url <source> <url>
reprocess-raw <source>
```

All three commands must enter the same pipeline at `raw HTML`.

- `batch`: find vacancies from source settings, save raw HTML, then continue.
- `from-url`: save that URL as raw HTML first, then continue.
- `reprocess-raw`: use already saved raw HTML, then continue.

## Stage Rules

- Do not skip stages.
- Do not duplicate a stage's internals in `WORKFLOW.md`.
- Before running a stage, use that stage's own file as the source of truth.
- If a stage is an agent step, Codex must execute that instruction instead of
  replacing it with an unrelated script.
- `scoring/candidate_fit/filter.py` is only the fast rejection gate. A passed
  fast filter is not a final positive candidate-fit score.
- Positive candidate fit must come from the semantic agent step in
  `scoring/candidate_fit/evaluate.md`.
- `workflow/save_analyzed_job.py` only saves fully scored JSON. It must not
  calculate `candidate_fit_percent` or `job_interest`.
- Database JSON mapping must go through `db/job_mapper.py`.

## Manual URL Rule

Manual URL debug uses the same raw pipeline:

```text
URL -> raw HTML -> readable text -> analyzed JSON -> scoring -> save
```

Do not bypass raw/readable/analyzed/scoring stages just because the URL was
provided manually.
