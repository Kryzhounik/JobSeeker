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

LinkedIn starts with the browser-only AccountRemote priority pass described in
`collector/scan_linkedin.md`: remote jobs first, before country hybrid/office
results. It shares the global limit and is not sent to the guest collector.

After that, collection is hybrid and location-by-location. For each configured
country, `collector/scan_linkedin.py` uses the public guest endpoints as a
cheap deterministic first pass. It saves reachable raw HTML and readable text
and stores source job IDs without spending model tokens on browser interaction.

The guest collector is not the sole LinkedIn collector. Measured guest and
logged-in browser searches returned different terminal job sets, and repeated
guest requests returned different counts. This can be caused by different
authenticated search inventory, personalization, ranking, caching, or guest
endpoint behavior; completing guest pagination therefore does not guarantee
the same inventory as the browser.

Missing ID overlap between adjacent guest pages is therefore diagnostic only:
the script records a warning and continues. It does not treat overlap as a
pagination requirement.

After one location's guest pass, recalculate the remaining global limit. If it
is zero, collection ends immediately without a browser gap-fill.
Otherwise, run the browser collector for that same location only as a gap-fill
for the remaining count. Its preview filter checks title and company first,
then checks `(source, source_job_id)` in `source_jobs`. Jobs already stored by
the guest pass are skipped before a details pane is opened. Move to the next
configured location only if the combined scope is still below the global
limit. Never run a coverage-only browser pass after the limit has been reached.

`source_jobs.collection_method` records which collector first persisted valid
raw data: `script` for the guest collector and `browser` for the logged-in
browser collector. Existing pre-migration rows use `unknown`. Later processing
stages do not overwrite the first collection method.

Do not remove the browser collector or describe `scan_linkedin.py` as a full
replacement for it. The guest script may also be run independently through
its `search-page` and `job` diagnostic commands. The workflow invokes its batch
mode as `python collector/scan_linkedin.py batch --location <Name[:geoId]> --limit <remaining>`.
