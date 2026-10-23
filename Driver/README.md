# Driver

`Driver` contains the executable vacancy workflow: collection, analysis,
scoring, database mapping, and persistence. The Java orchestrator owns the
execution order; individual modules own their internal rules.

## Java Workflow Orchestrator

`orchestrator` is the Java entry point for the workflow. It calls the existing
LinkedIn collector, sends the complete collected scope to the agent relevance
filter through the metered Codex CLI proxy, persists validated decisions as
`RELEVANT` or `NONRELEVANT`, then owns `job_facts`, candidate fit, job interest, and final
database save for the remaining ordered scope. Build the server modules with:

```powershell
mvn -f Driver/pom.xml package
```

`Driver/pom.xml` is the parent and reactor for Mailer, Tools, Applier, ChatEvaluator, LLM,
the LinkedIn collector, the workflow orchestrator, and the API module. Every module creates a
thin JAR in its own directory; runnable modules declare their main class in
the manifest. Runtime dependencies, including copies of required project
modules, go into the repository-root `libs/`. The manifest supplies their
relative paths, so no manual classpath is needed.

To build one module and its project dependencies, use the reactor, for example:

```powershell
mvn -f Driver/pom.xml -pl :applier -am package
```

JavaFX remains an independent Maven project, outside this parent and reactor.
Run `mvn -f Driver/pom.xml install` before its separate build so project
dependencies are available in the local Maven repository. The
[GUI build scripts](../GUI/JavaFX/README.md) perform both builds.

Java owns the primary workflow order while the existing Python modules keep
their analysis, scoring, validation, and persistence logic.

Analyzer worker counts and group size live in
`analyzer/config/execution.ini`. Java currently processes job-facts groups
sequentially; bounded parallel groups remain a backlog item. Candidate fit uses
one persistent blind evaluator per run so all vacancies share one scoring scale.

## Java API and CLI

[`api`](api/README.md) provides `com.seeker.api.DriverApi` and the thin runnable
`Driver/api/job-seeker-api.jar`. Java calls delegate to module APIs and return
their existing results. CLI commands delegate to module CLI handlers in the
same JVM, preserving output and exit codes. Individual modules remain usable
without this facade. JavaFX delegates collection, application preparation, and
ChatGPT evaluation to this Java API; database access remains in `ClientData`.

## Agent Relevance Filter

`analyzer/agent_filter.md` filters clearly irrelevant vacancies that passed the
scripted collector filters. It runs before detailed analysis so those vacancies
remain in the database with status `NONRELEVANT` without consuming full
analysis resources. In the primary `playwright` workflow this operation is
owned by the Java orchestrator and always uses the CLI transport.

## LinkedIn Collection

`collector/linkedin_collection.md` is the single LinkedIn collection entry
point. It reads `collectorMode` from `collector/config/linkedin.properties`
before opening an implementation-specific instruction.

`playwright` is the primary mode. The Java collector owns the complete search,
filtering, persistence, and collection scope without agent-driven browser work.

`agent` preserves the deprecated guest plus logged-in Chrome workflow in
`collector/deprecated_agent_collection/`. It is a rollback path being retired,
kept in case a LinkedIn UI change makes the Playwright collector unusable. That
directory is read only when this mode is explicitly required.

Both modes return the same `run_id` and ordered `scope`. In primary
`playwright` mode Java continues that scope through analysis and database save.
