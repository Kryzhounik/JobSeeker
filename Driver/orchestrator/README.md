# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. `process-batch linkedin`
calls the existing LinkedIn collector, sends its complete ordered scope to the
existing agent relevance filter through the metered Codex CLI proxy, validates
the returned IDs, marks accepted jobs `RELEVANT` and rejected jobs `NONRELEVANT`
in one transaction, runs grouped
`job_facts`, candidate fit, job interest, and the existing database save.

The Python proxy is reached through the process-wide JPy runtime already used
by the collector. No separate Python process is started for orchestration; the
proxy itself launches the configured Codex CLI command.

Build the collector and orchestrator together from the project root:

```powershell
mvn -f Driver/pom.xml package
```

The thin runnable JAR is `Driver/orchestrator/workflow-orchestrator.jar`.
Runtime dependencies are copied to the repository-root `libs/`; the manifest
references them automatically.

## Operations

The application performs these operations:

1. Collect LinkedIn vacancies and persist their raw/readable data.
2. Run the agent filter and atomically mark vacancies `RELEVANT` or `NONRELEVANT`.
3. Extract `job_facts` in configured groups.
4. Run the deterministic candidate-fit gate.
5. Evaluate the remaining vacancies in one persistent candidate-fit agent
   session.
6. Calculate `job_interest`.
7. Save the fully scored vacancies to SQLite.

## Console entry points

Run commands from the project root.

### Complete workflow

Runs all operations listed above. Currently `linkedin` is the only supported
source. Each saved source job records the ID of the collection run that first
created it.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar process-batch linkedin
```

Java callers use `WorkflowOrchestrator.processBatch("linkedin")` and receive a
`WorkflowResult`.

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
Pass `Consumer<WorkflowProgress>` to the individual operation, for example
`processBatch("linkedin", handler)` or `processUrls(urlsList, handler)`.
Orchestrator creates its own per-call Collector handler and adapts collection
notifications to the existing workflow snapshot. Lower modules never receive
the caller's workflow handler. The detailed workflow event contract is a separate
task; this change does not add snapshot fields. CLI notifications are emitted as
`WORKFLOW_PROGRESS ` JSON lines on stderr. JPy gateways do not
change global Python stdout; only the CLI temporarily redirects it to stderr.

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

Reads the completed collection's vacancies from SQLite and uses the same
status-based processing as a fresh batch. It requires the collection run ID
recorded on the vacancies. Completed stages are skipped:

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
`source`, `run_id`, `status`, `processed_scope`, `processed_count`, and `message`.
`processed_count` is computed from `processed_scope` when the result is created.
`StageResult`, `jobFacts()`, and `finishAnalysis()` are package-private;
these commands do not add a public Orchestrator API.

### LinkedIn collector utilities

These are lower-level collector commands. They do not run the title filter,
analysis, scoring, or final save.

```powershell
java -jar Driver/collector/java_linkedin/linkedin-collector.jar login
java -jar Driver/collector/java_linkedin/linkedin-collector.jar collect-batch
```

`login` opens the configured browser profile for LinkedIn authentication.
Collector `collect-batch` performs collection only. Normal production execution
should use the orchestrator's `process-batch linkedin` command.

There are currently no separate Java console commands for the title filter,
candidate fit, job interest, or database save. They are exposed only as parts
of `process-batch`, `process-urls`, `resume`, and `finish-analysis`.
