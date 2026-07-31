Purpose: dispatch every agent operation through one execution-mode switch.

Execution mode: `desktop`

The caller supplies the operation name, instruction, input, contexts, output
schema, run ID, and target.

- `desktop`: execute the supplied instruction as the current Desktop agent,
  using only the supplied input and contexts, and return the schema-compliant
  response to the caller. Keep at most six subagents open at once; close
  completed subagents before starting the next wave.
- `cli`: invoke `codex_proxy/metrics_proxy.py` with the supplied values as
  documented in `codex_proxy/README.md`, then return its response to the caller.

The caller owns result handling and persistence. Do not bypass this dispatcher
for agent operations.
