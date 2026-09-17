# Driver

`Driver` contains the executable vacancy workflow: collection, analysis,
scoring, database mapping, and persistence. The top-level execution contract is
`WORKFLOW.md`; individual modules own their internal rules.

## Java Workflow Orchestrator

`orchestrator` is the Java entry point for the workflow. It calls the existing
LinkedIn collector, sends the complete collected scope to the agent relevance
filter through the metered Codex CLI proxy, persists validated rejections as
`NONRELEVANT`, and returns the remaining ordered scope. Build the collector and
orchestrator together with:

```powershell
mvn -f Driver/pom.xml package
```

## Agent Execution Boundary

`agent_execution.md` is the single decorator and mode switch for Desktop-owned
agent operations. Analyzer modules describe an operation by supplying its
instruction, input, contexts, output schema, run ID, and target. The decorator
then either executes it in the current Desktop agent or sends it through the
metered Codex CLI proxy. Operations already owned by the Java orchestrator send
the same explicit contract directly to the proxy.

This boundary exists so Desktop and CLI execution can be switched without
changing analyzer instructions, result handling, scoring, or persistence.
Every future Desktop-owned agent operation must use it rather than selecting a
transport inside its own instruction. Java-owned operations select their
transport in the Java orchestrator.

Keep `agent_execution.md` deliberately short because it is runtime context.
Design rationale belongs here. CLI-specific packaging, invocation, and metrics
belong to `codex_proxy`; analyzer result validation, merge, and persistence stay
with the analyzer.

Analyzer worker counts and group size live in
`analyzer/config/execution.ini`. Job facts use bounded parallel groups because
they extract independent DTOs. Candidate fit deliberately uses one persistent
blind evaluator per run so all vacancies share one scoring scale.

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

Both modes return the same `run_id` and ordered `scope`; analysis and database
scoring continue independently of the selected collector.
