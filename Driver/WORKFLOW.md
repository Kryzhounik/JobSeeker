# JobSeeker Workflow

Purpose: top-level run order only.

This file does not define internals of collector, analyzer, or db.
Each stage owns its own rules in its own files.

## Roots

Run driver commands from this `Driver` folder unless a command says
otherwise.

For Python commands documented below, use `python` from PATH. Do not reuse
an absolute Python path from an earlier session. The Java collector owns its
separate Python runtime settings in `collector/java_linkedin/runtime.properties`.

- Project root: parent folder of `Driver`.
- Data root: `../Data`.
- GUI root: `../GUI`.

## Main Pipeline

```text
collector/source adapter
-> readable vacancy text in SQLite (raw HTML remains persisted)
-> one batch agent relevance filter over the collected scope
-> sequential grouped analyzer/job_facts/extract.md in Java
-> analyzer/analyze_job.md over the explicit scope
   -> one persistent analyzer/candidate_fit/evaluate.md agent for the run
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

For `batch linkedin`, read and execute `collector/linkedin_collection.md`.
That instruction selects exactly one configured collection implementation and
returns its `run_id` and ordered `scope`. Do not inspect or combine collector
implementations outside that instruction.

In `playwright` mode, the workflow command starts the Java workflow
orchestrator. It delegates collection to the existing Java collector, sends the
complete collected scope through the agent relevance filter using the metered
Codex CLI proxy, marks validated rejected IDs `NONRELEVANT`, runs grouped
`job_facts` for the remaining vacancies, and returns their ordered scope.

Before every Java workflow launch, rebuild the executable JAR from the project
root. A successful `compile` is not sufficient:

```text
mvn -q -f Driver/pom.xml -DskipTests package
```

The Java Playwright command must run outside the filesystem sandbox from its
first attempt. Resolve `java.executable` from the local
`collector/java_linkedin/runtime.properties`, invoke the command with
`sandbox_permissions="require_escalated"`, and request a reusable approval for
this exact prefix:

```text
[<java.executable>, "-jar", "Driver/orchestrator/target/job-seeker-orchestrator.jar", "batch", "linkedin"]
```

Do not make a preliminary sandboxed attempt. An `AccessDeniedException` for
`job-seeker-orchestrator.jar` during Playwright initialization means that this
launch rule was violated; it is not a LinkedIn login failure. Repeat the same
command with the required permission instead of retrying it in the sandbox or
diagnosing Java, the JAR, Playwright, or LinkedIn authentication.

Continue the main pipeline only for the returned explicit scope. In
`playwright` mode the returned scope is already agent-filtered; do not invoke
the relevance filter or `job_facts` a second time. Pass it directly to
`analyzer/analyze_job.md` for candidate fit and job interest. If none remain,
the run completes without analysis or save. The Java orchestrator does not run
candidate fit, job interest, or database save.

To run Java-owned `job_facts` for an already collected explicit scope without
collecting again, use:

```text
<java.executable> -jar Driver/orchestrator/target/job-seeker-orchestrator.jar job-facts <run-id> <source> <comma-separated-job-ids>
```

The deprecated `agent` collector mode retains the Desktop-owned relevance
filter step through `agent_execution.md`; the Java-owned CLI step applies only
to `playwright` mode.

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

Do not create a file solely to transport an in-memory result to the next
operation. Use a direct return, stdin/stdout, or an existing persisted workflow
artifact. Raw, analyzed, and scored files are workflow data; a temporary agent
response JSON is not. The documented browser-pane HTML is the only current
collector exception because the browser payload itself must cross a process
boundary before raw persistence.

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
  filters and the agent relevance filter in that batch. Content-filtered and
  agent-filtered source data stay available but are not part of the analyzer
  scope.

For any multi-file scope, prepare readable text for every vacancy, then invoke
the analyzer once with that explicit ordered scope:

```text
for each raw HTML file in the explicit scope:
    source adapter stores readable vacancy text in SQLite
Java orchestrator:
    -> agent relevance filter removes explicit nonrelevant IDs from the scope
    -> sequential grouped job facts using analyzer/config/execution.ini
analyzer/analyze_job.md processes the complete explicit scope
    -> deterministic candidate-fit gates
    -> one persistent blind candidate-fit agent for all passed vacancies
    -> job interest for every scored JSON
for each fully scored JSON in scope order:
    -> SQLite save
```

Save the exact completed scope in one call:

```text
python db/save.py --input ../Data/scored/<source> --source <source> --job-ids <comma-separated-source-job-ids>
```

Do not pass the whole scored directory without `--job-ids` during a scoped
run.

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
- The current Desktop agent executes `analyzer/analyze_job.md` once for the
  complete Java-analyzed scope. It must not repeat Java-owned `job_facts`.
- Every Desktop-owned agent operation inside that orchestrator must use
  `agent_execution.md`. Its `Execution mode` is the single switch between
  direct Desktop execution and the metered Codex CLI proxy. Agent operations
  already owned by the Java orchestrator invoke the metered CLI proxy directly.
- `codex_proxy/metrics_proxy.py` is a CLI transport only. It may package the
  explicitly supplied operation files and record metrics, but it must not
  choose operations, process their business results, or persist analyzed or
  scored JSON.
- `analyzer/candidate_fit/filter.py` is only the fast rejection gate. A passed
  fast filter is not a final positive candidate-fit score.
- Positive candidate fit must come from the semantic agent step in
  `analyzer/candidate_fit/evaluate.md`.
- Candidate-fit evaluation must be isolated from job-facts reasoning and the
  outer analyzer context. Use one persistent blind evaluator for all semantic
  fit operations in the run. It receives `analyzer/candidate_fit/evaluate.md`,
  `analyzer/config/resume.ini`, the analyzed JSON sequence, and its own prior
  results from that run. Do not read an existing scored JSON or database score.
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
    -> Java-owned job facts
    -> analyzer (candidate fit -> job interest)
    -> save
```

Do not bypass raw/readable/analyzer stages just because the URL was
provided manually.
