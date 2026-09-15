# Driver

`Driver` contains the executable vacancy workflow: collection, analysis,
scoring, database mapping, and persistence. The top-level execution contract is
`WORKFLOW.md`; individual modules own their internal rules.

## Agent Execution Boundary

`agent_execution.md` is the single decorator and mode switch for agent
operations. Analyzer modules describe an operation by supplying its
instruction, input, contexts, output schema, run ID, and target. The decorator
then either executes it in the current Desktop agent or sends it through the
metered Codex CLI proxy.

This boundary exists so Desktop and CLI execution can be switched without
changing analyzer instructions, result handling, scoring, or persistence.
Every future agent operation must use it rather than selecting a transport
inside its own instruction.

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
analysis resources.

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
