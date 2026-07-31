# JobSeeker Workflow

Purpose: top-level run order only.

This file does not define internals of collector, analyzer, or db.
Each stage owns its own rules in its own files.

## Roots

Run driver commands from this `Driver` folder unless a command says
otherwise.

- Project root: parent folder of `Driver`.
- Data root: `../Data`.
- GUI root: `../GUI`.

## Main Pipeline

```text
collector/source adapter
-> readable vacancy text (raw HTML remains persisted)
-> analyzer/analyze_job.md (executed by the current Desktop agent)
   -> analyzer/job_facts/extract.md
   -> analyzer/candidate_fit/evaluate.md
   -> analyzer/job_interest/calculate.py
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

All three commands must pass raw HTML through the source adapter before
analysis.

- `batch`: find vacancies from source settings, save raw HTML, then continue.
- `from-url`: save that URL as raw HTML first, then continue.
- `reprocess-raw`: use already saved raw HTML, then continue.

## LinkedIn Batch Contract

`batch linkedin` means:

```text
read LinkedIn collector settings
-> open LinkedIn search in the logged-in browser
-> inspect search result cards
-> apply the preview filter to each card
-> save accepted vacancy detail panes as raw HTML
-> continue the main pipeline for the explicit run scope
```

Every run must have an explicit processing scope. Do not infer scope by picking
one arbitrary file.

Every public command also creates one `run_id` before its first stage and passes
that same value to every metered Codex CLI operation in the run. Use a readable
unique value such as `20260730T120000Z-batch-linkedin`.

If source, run scope, or target stage is ambiguous, stop and ask before running.
Do not guess source, pick an arbitrary file, or choose a later pipeline stage by
yourself.

## Interruption Policy

- A temporary slowdown, timeout, failed browser command, or unresponsive plugin
  is not a reason to stop the run.
- Report the problem to the user immediately, keep retrying/resuming the same
  work, and include the incident in the final summary even if it recovered.
- Stop on Codex's initiative only when work is genuinely impossible to
  continue, user action is required, or continuing risks the account, data, or
  project state.

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
    source adapter prepares readable vacancy text
    -> analyzer/analyze_job.md (executed by the current Desktop agent)
       -> job facts
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

- Before any command reads or writes SQLite, run `db/migrate.py` or use a
  script that calls it internally. The database must be at the latest schema
  version before collector deduplication, analyzer scoring maintenance, saving, GUI
  mutation, or manual SQL work.
- Do not skip stages.
- Each stage records its own successful completion. A failed stage leaves the
  vacancy at its previous successfully completed stage.
- Do not duplicate a stage's internals in `WORKFLOW.md`.
- Before running a stage, use that stage's own file as the source of truth.
- If a stage is an agent step, Codex must execute that instruction instead of
  replacing it with an unrelated script.
- The current Desktop agent executes `analyzer/analyze_job.md` as the
  top-level analysis orchestrator.
- Every agent operation inside that orchestrator must use
  `agent_execution.md`. Its `Execution mode` is the single switch between
  direct Desktop execution and the metered Codex CLI proxy.
- `codex_proxy/metrics_proxy.py` is a CLI transport only. It may package the
  explicitly supplied operation files and record metrics, but it must not
  choose operations, process their business results, or persist analyzed or
  scored JSON.
- `analyzer/candidate_fit/filter.py` is only the fast rejection gate. A passed
  fast filter is not a final positive candidate-fit score.
- Positive candidate fit must come from the semantic agent step in
  `analyzer/candidate_fit/evaluate.md`.
- Candidate-fit evaluation must be isolated. Run it as
  a separate agent that receives only `analyzer/candidate_fit/evaluate.md`, the
  analyzed JSON, and `analyzer/config/resume.ini`. Do not pass
  analyzer context or read an existing score.
- `../Data/analyzed/<source>/` contains analysis facts only. It must not contain
  candidate-fit or job-interest fields in the main workflow.
- Candidate-fit scoring reads analyzed JSON and writes scored JSON under
  `../Data/scored/<source>/` with the same file name.
- New scored JSON must contain `candidate_fit_reason_code` and must not use
  `undefined`. `undefined` is only a database compatibility value for records
  that already existed before reason codes were introduced.
- `analyzer/job_interest/calculate.py --input <scored-json-or-dir>` adds
  `job_interest` to scored JSON before save. Its DB recalculation mode is
  maintenance, not the main pipeline.
- `db/save.py` only saves fully scored JSON. It must not
  calculate `candidate_fit_percent` or `job_interest`.
- `db/save.py` input is `../Data/scored/<source>/`, not
  `../Data/analyzed/<source>/`.
- Database JSON mapping must go through `db/job_mapper.py`.
- Normal collection must skip already known source vacancies before reopening
  or re-analyzing them. Source-specific collector rules define how.
- Re-evaluating an existing vacancy is a separate calibration/debug action, not
  part of the normal batch workflow.

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
URL -> source adapter persists raw HTML and prepares readable text
    -> analyzer (job facts -> candidate fit -> job interest)
    -> save
```

Do not bypass raw/readable/analyzer stages just because the URL was
provided manually.
