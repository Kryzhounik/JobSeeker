# JobSeeker API

One Java entry point and one CLI over the existing Collector, Orchestrator,
Applier, and ChatEvaluator modules. The facade delegates operations without
duplicating workflow logic, parsing module arguments, or translating results.
Each module keeps its independent Java API and CLI.

## Java

Use `com.seeker.api.DriverApi` from Maven artifact
`com.seeker:job-seeker-api:1.0-SNAPSHOT`. Its constructor accepts only the project
root. The facade can be reused: progress handlers belong to individual calls,
for example `api.collectBatch(collectionHandler)` or
`api.processBatch("linkedin", workflowHandler)`. URL processing with progress
uses `api.processUrls(urlsList, workflowHandler)`.
All progress overloads accept `Consumer<ProgressSnapshot>` from
`com.seeker.tools.api` (Tools). Collection and workflow snapshots expose
`runId`, `stage`, `action`, an immutable ordered `fields` map, and `details`.
Labels and values are ready for display; every snapshot replaces the previous one.
Orchestrator handles Collector notifications internally and keeps their details
in Batch snapshots during Collection. Later stages expose their own fields, and
completion provides summary statistics. Calls without a handler remain available.
Callbacks run on the calling worker thread; UI callers dispatch updates to
their UI thread. No handler is retained by the facade or its services.
Components are initialized once; browser and Python operations start only
when the corresponding operation is called. Methods return the original
module DTOs or the ChatEvaluator string and propagate module exceptions.

Codex-backed workflow operations and application preparation can throw
`com.seeker.codex.UsageLimitExceededException` (a checked exception from
`Tools`). It is propagated without conversion to a generic error. Its
`getRetryAt()` returns the reset time as an `Instant`, or `null` when unknown.
The API does not schedule retries. The workflow and Applier CLI commands use
exit code `3` for this condition and print it without an error stack trace.
ChatEvaluator receives the project root explicitly, without changing JVM
properties. This module does not contain GUI data access.

`evaluateWithChatGPT(url)` resolves a saved job by URL, evaluates it and saves
the answer when a job is found; it returns the complete answer text. An unknown
URL is evaluated normally without persistence. The GUI uses
`evaluateWithChatGPT(url, jobId)`, which skips lookup and returns
`GptEvaluation.Result`; the caller guarantees that URL and ID refer to the same
job. Both overloads use ChatEvaluator's common operation. The CLI accepts only URL.

## CLI

Build and run from the repository root:

```powershell
mvn -f Driver/pom.xml -pl :job-seeker-api -am package
java -jar Driver/api/job-seeker-api.jar <command> [args...]
```

The thin JAR uses the existing repository-root `libs/` through its manifest.
Commands are `login`, `collect-batch`, `process-batch <source>`,
`process-urls <url...>`, `check-recommendations`, `resume <run-id>`,
`job-facts|finish-analysis <run-id> <source> <comma-separated-job-ids>`,
`apply <url>`, and `chat-eval <url>`.

Commands call module CLI handlers in the same JVM, preserving their output,
diagnostics, and exit codes. Only `apply` and `chat-eval` remove their router
command name before delegation. Missing or unknown commands return code `2`.
No child Java process or additional result serialization is used.

`DriverApi.checkRecommendations()` delegates to Orchestrator and returns its
`WorkflowResult`: fetch recommendation URLs through Mailer, then run the existing
URL workflow. Empty mail output does not start collection or analysis.

Routing tests use stub handlers and do not start Python, a browser, or external
services. JavaFX calls this Java API for collection, application preparation,
and ChatGPT evaluation. General GUI reads and edits remain in `ClientData`;
GPT answer persistence belongs to the common ChatEvaluator operation.
Its optional process-mode collector continues to use the independent CLI.
