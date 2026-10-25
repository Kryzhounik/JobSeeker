# Codex Proxy

This module provides the metered Codex CLI adapter used by the Java workflow.
Its entry point is `metrics_proxy.py`, a thin adapter for `codex exec`.

The proxy boundary is transport-only:

- accept an explicit instruction, input, contexts, and output schema;
- invoke Codex CLI and return its final response;
- record CLI usage metrics in SQLite.

`config.ini` contains the CLI model and reasoning defaults, session and sandbox
settings, and model rates used for usage metrics.

`lean_context = true` strips the generic coding-agent context for proxy calls.
It uses the short `transport_instructions.md` as the model's base instructions,
omits automatic skills, project instructions and collaboration/permissions/app
instruction blocks, and disables unused native tools. Runtime sandbox and MCP
approval settings still apply. The small environment context, including the
current date and timezone, is retained.

Caller instructions, input, context files and output schema are passed as
before, as are model, reasoning effort and session continuation. Explicit
Playwright MCP tools remain available for browser calls. The overrides apply
only to this CLI invocation; no global Codex configuration is changed. Set
`lean_context = false` to restore the previous CLI context for comparison or
rollback. These settings were verified with Codex CLI 0.159.2; their definitions
are in the [official configuration schema](https://developers.openai.com/codex/config-schema.json).

The proxy must never choose analyzer operations or contain vacancy logic. It
must not interpret, merge, or persist analyzed/scored JSON; run filters or
scoring; update vacancy lifecycle state; or save jobs. Those responsibilities
belong to the caller that owns the operation.

CLI usage:

```text
python codex_proxy/metrics_proxy.py
  --run-id <run-id>
  --operation <operation>
  --target <target>
  --instruction <instruction-file>
  --input <input-file-or->          # - reads UTF-8 input from stdin
  --context <context-file>          # repeat when needed
  --output-schema <schema-file>
  --thread-id <persisted-thread-id> # omit for the first target input
  --model <model>                   # optional operation override
  --reasoning-effort <effort>       # optional operation override
  --allow-browser-tools             # Applier: permit Playwright MCP browser tools
  --browser-cdp-endpoint <url>      # Applier: configure Playwright MCP for a shared browser
```

If either optional override is omitted, that value comes from `config.ini`.
Overrides apply only to the current invocation and do not change the file.
Browser tools are prohibited by default for analyzer operations. Applier passes
`--allow-browser-tools` (or `allow_browser_tools=True` to `run`) to permit only
Playwright MCP browser tools in its agent instructions. Applier must also pass
`--browser-cdp-endpoint`; the proxy configures the Playwright MCP server for
that endpoint for this invocation only. Other operations remain without tools.

Install the browser-tool runtime once from the Seeker project root:

```powershell
npm ci --prefix Driver/codex_proxy
```

The proxy starts the installed Playwright MCP script directly with Node.js.
It does not run npm or npx during application preparation. The dependency
version is fixed in `package.json` and `package-lock.json`.

The command writes the final agent response to stdout. It stores only transport
usage metrics in SQLite.

Java callers use `com.seeker.codex.CodexClient` from `Tools` for both JPy agent
operations and Python-process calls. The client performs the call and unwraps
the response in one step. It throws the checked `UsageLimitExceededException`
when Codex usage is exhausted. `getRetryAt()` returns an `Instant`, or `null`
when the reset time was not supplied or recognized. Callers decide how to
handle or retry the operation.

The proxy records metrics before raising its Python `UsageLimitExceededError`.
`codex_proxy.bridge` carries that failure across JPy as a structured response;
`--json-result` provides the same protocol for process callers. Normal CLI
output stays unchanged when that flag is absent. Limit recognition and reset
time parsing are centralized in `errors.py`; Java callers never parse CLI text.
Time-only reset messages use the CLI process's local timezone and the next
occurrence of that time. Unrecognized formats leave the reset time unset.

In `codex_invocations`, the token columns, `weighted_tokens`, and
`estimated_credits` record usage for that invocation only and can be summed
directly by operation, model, target, or date. The `cumulative_*_tokens` columns
retain the original CLI session counters used to calculate each invocation's
usage. Zero-usage failures do not reset the baseline for a resumed session.
`codex_runs` and `codex_run_operations` contain sums and averages of these
per-invocation values for the whole run and for each operation within the run.

CLI sessions are persisted because `config.ini` sets `ephemeral = false`.
The first target input omits `--thread-id`; the programmatic `run(...)` result
contains the new thread ID. A caller that owns a continued target passes that
ID on every later input. The proxy then uses `codex exec resume`; it sends the
instruction and static contexts only on the first turn and sends only the new
input on continued turns. Target grouping, thread lifetime, result validation,
and persistence remain caller responsibilities.
