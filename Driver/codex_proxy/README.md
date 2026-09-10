# Codex Proxy

This module provides optional metered transports for agent instructions. Its
active implementation is `metrics_proxy.py`, a thin adapter for `codex exec`.

The proxy boundary is transport-only:

- accept an explicit instruction, input, contexts, and output schema;
- invoke Codex CLI and return its final response;
- record CLI usage metrics in SQLite.

`config.ini` is the shared model configuration for both CLI calls and Desktop
subagents dispatched through `../agent_execution.md`. Desktop callers pass its
`model` and `reasoning_effort` explicitly instead of inheriting the
orchestrator's settings.

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
  --model <model>                   # optional operation override
  --reasoning-effort <effort>       # optional operation override
```

If either optional override is omitted, that value comes from `config.ini`.
Overrides apply only to the current invocation and do not change the file.

The command writes the final agent response to stdout. It stores only transport
usage metrics in SQLite.

When `agent_execution.md` enables comparison mode, the outer Desktop
orchestrator runs both transports and stores their raw responses with:

```text
python codex_proxy/comparison.py
  --operation-id <unique-logical-operation-id>
  --operation-type <operation>
```

The command reads `desktop_response` and `cli_response` from one UTF-8 JSON
object on stdin. Comparison storage does not choose or alter the response used
by the workflow.

`app_server_experiment/` contains a postponed alternative transport and is not
part of the active workflow.
