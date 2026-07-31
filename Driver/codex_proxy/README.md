# Codex Proxy

This module provides optional metered transports for agent instructions. Its
active implementation is `metrics_proxy.py`, a thin adapter for `codex exec`.

The proxy boundary is transport-only:

- accept an explicit instruction, input, contexts, and output schema;
- invoke Codex CLI and return its final response;
- record CLI usage metrics in SQLite.

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
  --input <input-file>
  --context <context-file>          # repeat when needed
  --output-schema <schema-file>
```

The command writes the final agent response to stdout. It stores only transport
usage metrics in SQLite.

`app_server_experiment/` contains a postponed alternative transport and is not
part of the active workflow.
