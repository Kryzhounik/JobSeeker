Purpose: dispatch every agent operation through one execution-mode switch.

Default execution mode: `desktop`
Default comparison mode: `off`

The caller may explicitly choose `desktop` or `cli` for a particular agent
operation. An explicit per-operation choice overrides the default for that
operation only. If the caller does not specify a mode, use the default above.

The caller may explicitly enable comparison mode for a particular operation.
When comparison mode is enabled:

1. Assign one unique `operation_id` to the logical operation.
2. Execute the same instruction, input, contexts, and output schema once with
   `desktop` and once with `cli`.
3. Do not give either execution the other execution's response.
4. Return only the response from the selected execution mode to the owning
   workflow.
5. Send both raw responses as one UTF-8 JSON object on standard input to:
   `python codex_proxy/comparison.py --operation-id <operation-id> --operation-type <operation>`.

The comparison record is diagnostic only. It must not change which response
the owning workflow uses. If comparison mode is not explicitly enabled, use
the default above and execute only the selected mode.

When creating a target, the caller supplies the operation name, instruction,
static contexts, output schema, run ID, and target. A continued target keeps
that setup and receives only the next input plus any input-specific context.

- `desktop`: execute the supplied instruction as the current Desktop agent,
  using only the supplied input and contexts, and return the schema-compliant
  response to the caller. The owning orchestrator defines concurrency and
  target lifetime. When it reuses a target, continue that same agent thread;
  do not create a new agent for every input.
- `cli`: invoke `codex_proxy/metrics_proxy.py` with the supplied values as
  documented in `codex_proxy/README.md`, then return its response to the caller.

The caller owns result handling and persistence. Do not bypass this dispatcher
for agent operations. If the selected execution mode cannot preserve a target
that the owning orchestrator requires to be reused, stop instead of silently
creating a replacement agent.
