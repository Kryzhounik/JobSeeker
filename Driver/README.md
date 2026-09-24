# Driver

`Driver` contains the executable vacancy workflow: collection, analysis,
scoring, database mapping, and persistence. The Java orchestrator owns the
execution order; individual modules own their internal rules.

## Java Workflow Orchestrator

`orchestrator` is the Java entry point for the workflow. It calls the existing
LinkedIn collector, sends the complete collected scope to the agent relevance
filter through the metered Codex CLI proxy, persists validated rejections as
`NONRELEVANT`, then owns `job_facts`, candidate fit, job interest, and final
database save for the remaining ordered scope. Build the collector and
orchestrator together with:

```powershell
mvn -f Driver/pom.xml package
```

## Agent Execution Boundary

`agent_execution.md` remains the boundary for deprecated Desktop-owned agent
operations. The primary Java workflow sends its explicit agent-operation
contracts directly through the metered Codex CLI proxy.

Java owns the primary workflow order while the existing Python modules keep
their analysis, scoring, validation, and persistence logic.

Keep `agent_execution.md` deliberately short because it is runtime context.
Design rationale belongs here. CLI-specific packaging, invocation, and metrics
belong to `codex_proxy`; analyzer result validation, merge, and persistence stay
with the analyzer.

Analyzer worker counts and group size live in
`analyzer/config/execution.ini`. Java currently processes job-facts groups
sequentially; bounded parallel groups remain a backlog item. Candidate fit uses
one persistent blind evaluator per run so all vacancies share one scoring scale.

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
and that directory is read only when this mode is explicitly required.

Both modes return the same `run_id` and ordered `scope`. In primary
`playwright` mode Java continues that scope through analysis and database save.
