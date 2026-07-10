# JobSeeker Workflow

Purpose: top-level run order only.

This file does not define internals of collector, analyzer, scoring, or db.
Each stage owns its own rules in its own files.

## Roots

Run driver commands from this `Driver` folder unless a command says
otherwise.

- Project root: parent folder of `Driver`.
- Data root: `../Data`.
- GUI root: `../GUI`.

## Main Pipeline

```text
collector
-> raw HTML
-> analyzer/extract_readable_text_v2.py
-> readable text
-> analyzer/analyze_job.md
-> analyzed JSON
-> scoring/candidate_fit/evaluate.md
-> candidate-fit-scored JSON
-> scoring/job_interest
-> fully scored JSON
-> db/save.py
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

## LinkedIn Batch Contract

`batch linkedin` means:

```text
read LinkedIn collector settings
-> open LinkedIn search in the logged-in browser
-> inspect search result cards
-> apply the preview filter to each visible card
-> save accepted vacancy detail panes as raw HTML
-> continue the main pipeline for the explicit run scope
```

Every run must have an explicit processing scope. Do not infer scope by picking
one arbitrary file.

Current MVP scopes:

- `from-url linkedin <url>`: process only that URL's saved raw file.
- `reprocess-raw linkedin`: process every raw HTML file currently present in
  `../Data/raw/linkedin/pages/`.
- `batch linkedin`: process the raw files saved by that batch run. If the batch
  starts from a clean workspace, this is the same as all raw files in
  `../Data/raw/linkedin/pages/`.

For any multi-file scope, iterate all files in that scope and run the main
pipeline for each file:

```text
for each raw HTML file in the explicit scope:
    raw HTML
    -> readable text
    -> analyzed JSON
    -> candidate fit
    -> job interest
    -> SQLite save
```

Do not pick one arbitrary raw/readable/analyzed file from a batch unless the
user explicitly asks for a single-id debug run.

If collection is split by the Codex five-minute tool-call limit, a temporary
checkpoint may record where to resume the search UI, such as search URL,
page/card offset, and counters. That checkpoint does not change the processing
scope.

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
- `db/save.py` only saves fully scored JSON. It must not
  calculate `candidate_fit_percent` or `job_interest`.
- Database JSON mapping must go through `db/job_mapper.py`.

## GUI Rule

The `../GUI/` app is a user-facing viewer only. It is not a Codex/agent
driver.

Agents must not use the GUI to run the workflow, inspect batch state, validate
records, or mutate data. Use files, SQLite queries, and workflow scripts
instead. Open or interact with the GUI only when the user explicitly asks for
GUI viewing/debugging.

## Manual URL Rule

Manual URL debug uses the same raw pipeline:

```text
URL -> raw HTML -> readable text -> analyzed JSON -> scoring -> save
```

Do not bypass raw/readable/analyzed/scoring stages just because the URL was
provided manually.
