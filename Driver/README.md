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

## LinkedIn Collection

LinkedIn collection is hybrid and location-by-location. For each configured
location, `collector/scan_linkedin.py` uses the public guest endpoints as a
cheap deterministic first pass. It saves reachable raw HTML and readable text
and stores source job IDs without spending model tokens on browser interaction.

The guest collector is not the sole LinkedIn collector. Measured guest and
logged-in browser searches returned different terminal job sets, and repeated
guest requests returned different counts. This can be caused by different
authenticated search inventory, personalization, ranking, caching, or guest
endpoint behavior; completing guest pagination therefore does not guarantee
the same inventory as the browser.

Immediately after one location's guest pass, run the logged-in browser
collector for that same location. Its preview filter checks title and company
first, then checks `(source, source_job_id)` in `source_jobs`. Jobs already
stored by the guest pass are skipped before a details pane is opened. The
browser therefore acts as a coverage pass for browser-only vacancies while
avoiding repeated per-vacancy browser work for the overlap. Move to the next
configured location only after both passes finish for the current one.

`source_jobs.collection_method` records which collector first persisted valid
raw data: `script` for the guest collector and `browser` for the logged-in
browser collector. Existing pre-migration rows use `unknown`. Later processing
stages do not overwrite the first collection method.

Do not remove the browser collector or describe `scan_linkedin.py` as a full
replacement for it. The guest script may also be run independently through
its `search-page` and `job` diagnostic commands. The workflow invokes its batch
mode as `python collector/scan_linkedin.py batch --location <Name[:geoId]>`.
