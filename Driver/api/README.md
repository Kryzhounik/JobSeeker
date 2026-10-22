# JobSeeker API

One Java entry point and one CLI over the existing Collector, Orchestrator,
Applier, and ChatEvaluator modules. The facade delegates operations without
duplicating workflow logic, parsing module arguments, or translating results.
Each module keeps its independent Java API and CLI.

## Java

Use `com.seeker.api.DriverApi` from Maven artifact
`com.seeker:job-seeker-api:1.0-SNAPSHOT`. Its constructor accepts the project
root and, optionally, the existing `Consumer<CollectionProgress>` callback.
Components are initialized once; browser and Python operations start only
when the corresponding operation is called. Methods return the original
module DTOs or the ChatEvaluator string and propagate module exceptions.
ChatEvaluator receives the project root explicitly, without changing JVM
properties. This module does not contain GUI data access.

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
and ChatGPT evaluation, while keeping its database access in `ClientData`.
Its optional process-mode collector continues to use the independent CLI.
