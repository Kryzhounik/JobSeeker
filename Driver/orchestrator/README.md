# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. `process-batch`
calls Collector with the configured sources, runs synchronous observational description
deduplication on the saved batch, then sends its complete ordered scope to the
existing agent relevance filter through the metered Codex CLI proxy, validates
the returned publication identities, marks accepted jobs `RELEVANT` and rejected jobs `NONRELEVANT`
in one transaction, runs grouped
`job_facts`, candidate fit, job interest, and the existing database save.

The module calls existing Python operations through JPy. Shared boundary rules
are described in
[Driver architecture](../ARCHITECTURE.md#граница-java-и-python-через-jpy).

`python_bridge.py` only converts JPy parameters and results and delegates to
`processing.py`. The latter is the Python application API: it owns database
operations, model calls, validation, artifact persistence and session recovery.
It can be imported as `orchestrator.processing` with `Driver` on the Python
module path, using `Path`, lists and dictionaries directly, without JPy.

Build the collector and orchestrator together from the project root:

```powershell
mvn -f Driver/pom.xml package
```

The thin runnable JAR is `Driver/orchestrator/workflow-orchestrator.jar`.
Runtime dependencies are copied to the repository-root `libs/`; the manifest
references them automatically.

## Operations

The application performs these operations:

1. Collect configured sources and persist their raw/readable data.
   After successful collection, call `easyReasoning.deduplicateBatch(runId)`
   once before relevance filtering. This also applies to URL collection and email
   recommendations. The operation owns its `off`/`qwen` setting (see
   [EasyReasoning documentation](../../EasyReasoning/README.md)).
   Duplicate links do not exclude vacancies from subsequent processing;
   model errors are visible and do not stop analysis. Resume is unchanged.
2. Run the agent filter and atomically mark vacancies `RELEVANT` or `NONRELEVANT`.
3. Extract `job_facts` in configured groups.
4. Run the deterministic candidate-fit gate.
5. Evaluate the remaining vacancies in one persistent candidate-fit agent
   session.
6. Calculate `job_interest`.
7. Save the fully scored vacancies to SQLite.

Job-facts groups run in a fixed thread pool sized by `parallel_agents` in
`Driver/analyzer/config/execution.ini` (currently 3). Each group processes up to
`vacancies_per_agent` vacancies sequentially in its own persistent agent session.
Progress reports only the total completed count, for example `34 / 89`, after
each successful vacancy; notifications are emitted on the workflow caller's thread.
On failure no further turns are scheduled, already running turns finish, and
the original failure is propagated. Completed facts remain available for resume.

## Console entry points

Run commands from the project root.

### Complete workflow

Runs all operations listed above. Without an argument, `process-batch` uses
`sources` from `Driver/collector/config/collector.properties`. An explicit
`linkedin` or `justjoin` argument selects only that source for the invocation.
Both sources run sequentially in one batch with separate configured limits.
Each saved source job records the ID of the collection run that first
created it.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar process-batch
```

Java callers use `WorkflowOrchestrator.processBatch()` and receive a
`WorkflowResult`. Existing `processBatch("linkedin")` explicitly selects LinkedIn;
`processBatch("justjoin")` explicitly selects JustJoinIT.

### Process LinkedIn vacancies by URL

Accepts one or more URLs with the same duplicate, preview, and content filters
as batch collection. URLs are collected sequentially in one browser session
under one `run_id`. After collection, operations 2–7 run for the complete
accepted scope, preserving the URL order. The search batch limit does not
truncate an explicit URL list.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar process-urls "<linkedin-job-url>"
java -jar Driver/orchestrator/workflow-orchestrator.jar process-urls "<url-1>" "<url-2>"
```

The command prints a `WorkflowResult` as JSON:

- `source`, `run_id`, `status`, and `message` identify the workflow result.
- `collection` is a `WorkflowResult.CollectionSummary` without page diagnostics.
  Its `accepted_count`, `scope`, and `outcomes` retain their collection-only meaning.
  Collector APIs return the full, unchanged `CollectionReport`, including `pages`.
- `processed_scope` and `processed_count` describe the accepted workflow scope
  after relevance filtering and analysis, including already completed jobs.
  `processed_count` is a result component computed from `processed_scope` on creation.
- `agent_filtered_count` counts vacancies rejected by the agent filter.
- `saved_count` counts confirmed new saves during this invocation, excluding
  skipped records and vacancies already saved before the call. It is not
  `processed_count` or the input scope size.

`status: "complete"` means processing finished, not that any vacancy was
accepted. A duplicate or rejected vacancy has an empty `processed_scope` and
an explanatory `message`. For multiple URLs, the workflow scope preserves URL
order, while `collection` retains the combined collection statistics. A
collection failure stops the run before analysis and returns an empty
processed scope alongside a summary of the failed collection report.

Java callers use one method, `WorkflowOrchestrator.processUrls(String... urls)`:
`processUrls(url)` for one URL, `processUrls(url1, url2)` for several, or
`processUrls(urlArray)`.
The CLI parses arguments, installs its output policy, and prints the report.
Java callers receive `WorkflowResult`; Collector APIs still return
`CollectionReport`. The CLI serializes `WorkflowResult` directly without editing
its JSON. `WorkflowOrchestrator(projectRoot)` keeps no progress handler.
Pass `Consumer<ProgressSnapshot>` (`com.seeker.tools.api`) to the individual operation, for example
`processBatch("linkedin", handler)` or `processUrls(urlsList, handler)`.
Orchestrator creates its own per-call Collector handler and retains all collection
details during the Collection stage. Other stages expose only their own fields;
the final snapshot provides summary statistics. Lower modules never receive the caller's
workflow handler. Snapshots expose `runId`, `stage`, `action`, compact `progress`, immutable ordered
`fields`, and `details`, with display-ready labels and values. Each replaces the
previous snapshot. Detailed stages are `Collection`, `Relevance filter`, `Job facts`,
and `Candidate fit`; `progress` shows stage counters rather than the current activity.
Initialization is an action, not a separate stage. Job interest
and saving still run in the same order without separate panel stages or saving
events. Final statistics include actual `Saved` writes and a separate `Already saved`
count. An idle repeat of a completed run therefore reports zero new saves.
Codex exhaustion leaves the last progress snapshot unchanged and propagates the original
`UsageLimitExceededException` with its `retryAt`. There is no automatic retry.

Batch state is persisted in the existing `collection_runs` row.
Migration 047 renames `linkedin_collection_runs` without recreating batch rows;
vacancy links and `linkedin_collection_pages` diagnostics keep their foreign keys.
GUI's Runs query uses `db.collection_runs` rather than embedding batch SQL.
`status` is `running`, `pending`, `failed`, or `complete`;
`stage` records Collection, Relevance filter, Job facts, Candidate fit, or Saving.
The collector's own result/timestamp are kept in `collection_status` and
`collection_finished_at`. A successful standalone Collect leaves the batch `pending`.
In a full Batch, the orchestrator immediately continues as `running` after collection.
Reaching the collection limit does not finish the batch.
Failures keep the last stage; resume still selects work by individual vacancy statuses.
Resume accepts `pending` and `failed` batches with completed collection;
`running` and `complete` are rejected without changing their state.
Partial analysis commands leave `pending` when further work remains; completion requires no unfinished
vacancies in the batch. Saving remains absent from intermediate GUI progress events.
Migration 042 preserves IDs and existing vacancy/page links. Historical collection
completion alone is not treated as proof that the whole workflow completed;
historical rows are marked `failed`, not active `running`.
CLI retains exit code `3` for that expected limit, rather than an application-error
stack trace. CLI notifications are emitted as
`WORKFLOW_PROGRESS ` JSON lines on stderr.

## Source identity

Processing identifies every publication by `(source, job_id)`, including status
lookups, relevance decisions and accepted/rejected result matching. The agent
returns `nonrelevant_jobs` as ordered `{source, job_id}` objects. The title-only
decision rules are unchanged. Fact extraction, fit, interest and saving use each
scope item's source; the shared candidate-fit session still spans the whole run.
Resume loads saved work for every source and does not collect again. Its summary
`source` is the sole source, `mixed` for multiple sources, or empty for an empty
scope. Scope entries always retain the actual source. Partial-stage results also
derive their summary from nonempty scope; existing method signatures remain.

Normal batch collection uses the configured source selection. Direct URLs and
email recommendations retain their LinkedIn-only collection path. `source_results`
inside the collection summary retains source-specific counts and limits.
Semantic comparison remains in EasyReasoning and uses saved registry IDs for
links, so identical external IDs in different sources remain separate records.

### Check email recommendations

```powershell
java -jar Driver/api/job-seeker-api.jar check-recommendations
```

Also available through `WorkflowOrchestrator.checkRecommendations()`,
`DriverApi.checkRecommendations()`, and the standalone Orchestrator CLI.
Mailer supplies canonical URLs; the existing URL workflow processes them together.
The collection run ID starts with `recommendations-`; newly saved source jobs
retain that ID in `collection_run_id`. No new recommendations means an empty
successful `WorkflowResult` with null `run_id` and `collection`, without collection.
Mail errors propagate without starting the pipeline. Mailer's checkpoint still
tracks successful URL extraction, independently of subsequent vacancy processing.

### Resume a partially processed run

Reads saved vacancies from SQLite, including after a failed collection, and uses
the same status-based processing as a fresh batch. The database records determine
the scope, even if collection stopped before updating its accepted count. It
requires the collection run ID recorded on the vacancies. Collection is not
restarted; running and fully completed batches cannot be resumed. Completed
processing stages are skipped:

| Current status | Next operation |
| --- | --- |
| `CLEANED` | Agent filter |
| `RELEVANT` | Job facts |
| `ANALYZED` | Candidate fit and job interest |
| `SCORED` | Final database save |
| `NONRELEVANT`, `SAVED` | No further work |

Candidate fit and job interest form one scoring stage; `SCORED` is written
after both succeed for a vacancy. When resuming, semantic candidate fit reuses
the run's existing evaluator session.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar resume <run-id>
```

The resume JSON includes `"collection": null` because no new collection took
place. CLI serialization retains null fields. Its `run_id`, `processed_scope`,
and filtering count identify the resumed work. No synthetic `CollectionReport`
is created.

### Job-facts only

Runs operation 3 for an existing explicit scope. It does not collect vacancies
or continue to candidate fit.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar job-facts <run-id> <source> <comma-separated-job-ids>
```

### Finish analysis

Runs operations 4–7 for a scope whose `job_facts` files already exist.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar finish-analysis <run-id> <source> <comma-separated-job-ids>
```

Both explicit-scope commands serialize their `StageResult` directly as JSON:
`source`, `run_id`, `status`, `processed_scope`, `processed_count`, `saved_count`, and `message`.
`processed_count` is computed from `processed_scope` when the result is created.
`jobFacts()` and `finishAnalysis()` are also available through the Java facade.
Their `saved_count` follows the same confirmed-write rule; job-facts-only returns zero.

### LinkedIn collector utilities

These are lower-level collector commands. They do not run the title filter,
analysis, scoring, or final save.

```powershell
java -jar Driver/collector/java_linkedin/linkedin-collector.jar login
java -jar Driver/collector/java_linkedin/linkedin-collector.jar collect-batch
```

`login` opens the configured browser profile for LinkedIn authentication.
Collector `collect-batch` performs collection only. Normal production execution
should use the orchestrator's `process-batch` command.

There are currently no separate Java console commands for the title filter,
candidate fit, job interest, or database save. They are exposed only as parts
of `process-batch`, `process-urls`, `resume`, and `finish-analysis`.
