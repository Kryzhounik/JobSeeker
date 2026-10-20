# Java workflow orchestrator

This module is the Java entry point for the JobSeeker workflow. `batch linkedin`
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
java -jar Driver/orchestrator/workflow-orchestrator.jar batch linkedin
```

### Check LinkedIn vacancies by URL

Accepts one or more URLs with the same duplicate, preview, and content filters
as batch collection. URLs are collected sequentially in one browser session
under one `run_id`. After collection, operations 2–7 run for the complete
accepted scope, preserving the URL order. The search batch limit does not
truncate an explicit URL list.

```powershell
java -jar Driver/orchestrator/workflow-orchestrator.jar check-url "<linkedin-job-url>"
java -jar Driver/orchestrator/workflow-orchestrator.jar check-url "<url-1>" "<url-2>"
```

The command prints a `WorkflowResult` as JSON:

- `source`, `run_id`, `status`, and `message` identify the workflow result.
- `collection` is the unchanged Collector report (CLI omits its `pages`). Its
  `accepted_count`, `scope`, and `outcomes` retain their collection-only meaning.
- `processed_scope` and `processed_count` describe the accepted workflow scope
  after relevance filtering and analysis, including already completed jobs.
- `agent_filtered_count` counts vacancies rejected by the agent filter.

`status: "complete"` means processing finished, not that any vacancy was
accepted. A duplicate or rejected vacancy has an empty `processed_scope` and
an explanatory `message`. For multiple URLs, the workflow scope preserves URL
order, while `collection` retains the combined collection statistics. A
collection failure stops the run before analysis and returns an empty
processed scope alongside the original failed collection report.

Java callers use one method, `WorkflowOrchestrator.check(String... urls)`:
`check(url)` for one URL, `check(url1, url2)` for several, or `check(urlArray)`.
The CLI parses arguments, installs its output policy, and prints the report.
Java callers receive `WorkflowResult`; Collector APIs still return
`CollectionReport`. `WorkflowOrchestrator(projectRoot, progressListener)` can
forward Collector callbacks without any console transport. JPy gateways do not
change global Python stdout; only the CLI temporarily redirects it to stderr.

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

The resume result has no `collection` report because no new collection took
place. Its `run_id`, `processed_scope`, and filtering count identify the resumed
work. No synthetic `CollectionReport` is created.

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

The explicit-scope commands return `processed_count` and `processed_scope`,
not collection acceptance counters.

### LinkedIn collector utilities

These are lower-level collector commands. They do not run the title filter,
analysis, scoring, or final save.

```powershell
java -jar Driver/collector/java_linkedin/linkedin-collector.jar login
java -jar Driver/collector/java_linkedin/linkedin-collector.jar batch
```

`login` opens the configured browser profile for LinkedIn authentication.
Collector `batch` performs collection only. Normal production execution should
use the orchestrator's `batch linkedin` command.

There are currently no separate Java console commands for the title filter,
candidate fit, job interest, or database save. They are exposed only as parts
of `batch`, `check-url`, `resume`, and `finish-analysis`.
