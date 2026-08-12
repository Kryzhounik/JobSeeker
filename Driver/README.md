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

## Repeated Command Execution

Never execute a repeated workflow step by generating a new long inline shell
command for every item or batch. Changing command text causes repeated sandbox
approval prompts and makes identical operations behave differently.

Before the second execution of the same command sequence:

1. Put the deterministic sequence behind one stable project command or module.
2. Pass changing values only as arguments or input files.
3. Reuse the same short command prefix for every item and batch.
4. Keep business decisions in the owning workflow/module; the command may only
   automate the already-defined sequence.

One approval for a stable command prefix is acceptable. Repeated approval
dialogs caused by per-batch command construction are not.
