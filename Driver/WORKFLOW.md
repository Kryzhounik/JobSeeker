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
-> readable vacancy text in SQLite (raw HTML remains persisted)
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
-> for each configured location in order:
   -> run collector/scan_linkedin.py batch --location <location>
   -> fetch guest-search pages in steps of 9
   -> save accepted guest raw/readable data with collection_method=script
   -> recalculate remaining = global limit - combined run scope size
   -> if remaining is zero, stop collection immediately
   -> otherwise run the logged-in browser collector for that same location
   -> skip guest-prefetched IDs through normal preview deduplication
   -> save browser-only raw/readable data with collection_method=browser
   -> merge both location scopes into the explicit run scope
   -> recalculate remaining = global limit - combined run scope size
   -> if remaining is zero, stop collection immediately
   -> only then move to the next location
-> continue the main pipeline for the explicit run scope
```

The guest prefetch command is:

```text
python collector/scan_linkedin.py batch --location <Name[:geoId]>
```

During a workflow batch, consume this command's JSON result directly from
stdout. Do not pass `--output`, create a scope file, or invent a temporary
directory for the result. The returned `scope` list is the in-memory list of
source-job IDs that the caller merges into the current run scope.

For diagnostics, use its `search-page` and `job` commands. The guest prefetch
does not replace the browser coverage pass. Do not replace a failing guest or
browser operation with an undocumented alternative during the batch.

The global limit applies to the combined scope. The caller tracks the remaining
limit across both passes and all locations; neither collector may independently
restart the limit for the next location. The browser pass is only a gap-fill up
to that limit. If the guest pass reaches the global limit, do not invoke the
browser collector, inspect browser coverage, or continue to another location.

LinkedIn guest responses are not stable snapshots. If adjacent `start` pages
have no overlapping job ID, `scan_linkedin.py` logs a pagination warning and
continues. This warning is not a batch failure or a Strict Batch Policy stop
condition. Deduplication and two consecutive pages without new IDs remain the
exhaustion safeguards.

Every run must have an explicit processing scope. Do not infer scope by picking
one arbitrary file.

Every public command also creates one `run_id` before its first stage and passes
that same value to every metered Codex CLI operation in the run. Use a readable
unique value such as `20260730T120000Z-batch-linkedin`.

If source, run scope, or target stage is ambiguous, stop and ask before running.
Do not guess source, pick an arbitrary file, or choose a later pipeline stage by
yourself.

## Command Execution Policy

Never execute a repeated workflow step by generating a new long inline shell
command for every item or batch. Before the second execution of the same
command sequence:

1. Put the deterministic sequence behind one stable project command or module.
2. Pass changing values only as arguments or input files.
3. Reuse the same short command prefix for every item and batch.
4. Keep business decisions in the owning workflow/module; the command may only
   automate the already-defined sequence.

One approval for a stable command prefix is acceptable. Repeated approval
dialogs caused by per-batch command construction are not.

## Strict Batch Execution, Waiting, And Stop Policy

A batch is production execution of an already documented workflow. It is not a
debugging, research, development, or workflow-design session.

During a batch, Codex MUST execute only the exact operations, commands,
navigation steps, and recovery branches that were documented in `WORKFLOW.md`
and the owning stage instructions before the batch started.

A temporary delay is not a workflow failure. A slow response, timeout, or
temporarily unresponsive plugin MUST NOT stop the batch by itself. Codex may
report the delay, wait, and retry the exact same operation with the same
command, selector, API, browser tab, inputs, and parameters. When that exact
operation succeeds, continue the documented workflow normally. Waiting longer
before repeating the same operation is allowed; inventing a different way to
perform it is not.

The mandatory stop boundary is not "an operation timed out." The mandatory
stop boundary is "continuing would require departing from the documented
workflow." If the exact documented operation does not recover and the next
step would require a different method, an undocumented recovery, diagnosis, or
manual repair, Codex MUST:

1. Stop the entire batch at the last successfully completed stage before
   trying that alternative.
2. Do not start or continue any other stage, including work that could run
   independently on already collected vacancies.
3. Report the problem to the user with the `run_id`, stage, affected page/card
   or source-job ID, expected behavior, observed behavior or error, completed
   scope count, exact retries already performed, and last successful
   checkpoint.
4. Wait for the user before performing diagnosis, testing a workaround,
   changing instructions, or resuming/restarting the batch.

The following actions are strictly prohibited inside a running batch unless
the exact action is already an explicit recovery step in the owning
instructions:

- retrying an operation with a different command, selector, API, browser
  surface, navigation strategy, parameters, or batch size;
- reloading, replacing, reopening, or creating browser tabs as a workaround;
- running exploratory diagnostics, experiments, probes, or one-off shell code;
- creating or modifying project code, configuration, instructions, schemas, or
  database structure;
- synthesizing fallback input, manually repairing an intermediate result, or
  bypassing a failed stage;
- accepting partial completion and continuing the remainder of the pipeline.

Repeating the same operation after waiting is allowed for a plausibly transient
failure. A documented alternative recovery may be performed only with its
documented command and conditions. Codex must never turn a transient retry into
an improvised alternative approach.

Diagnosis and solution design happen only after the batch has stopped and the
user has approved that separate work. A discovered solution becomes available
to later runs only after it is written into the owning workflow/stage
instructions. The stopped batch may resume only after the user explicitly asks
for it.

Current MVP scopes:

- `from-url linkedin <url>`: process only that URL's saved raw file.
- `reprocess-raw linkedin`: process every raw HTML file currently present in
  `../Data/raw/linkedin/pages/`.
- `batch linkedin`: process the source-job IDs accepted by both collector
  filters in that batch. Content-filtered raw HTML and stored readable text stay
  available for calibration but are not part of the analyzer scope.

For any multi-file scope, iterate all files in that scope and run the main
pipeline for each file:

```text
for each raw HTML file in the explicit scope:
    source adapter stores readable vacancy text in SQLite
    -> analyzer/analyze_job.md (executed by the current Desktop agent)
       -> job facts
       -> candidate fit
       -> job interest
    -> SQLite save
```

Do not pick one arbitrary raw file, readable DB row, or analyzed file from a batch unless the
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
URL -> source adapter persists raw HTML and stores readable text in SQLite
    -> analyzer (job facts -> candidate fit -> job interest)
    -> save
```

Do not bypass raw/readable/analyzer stages just because the URL was
provided manually.
