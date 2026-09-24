Purpose: dispatch Desktop-owned agent operations through one execution-mode
switch.

This dispatcher owns agent operations orchestrated by the Desktop workflow.
Agent operations moved into the Java workflow orchestrator are no longer
Desktop-dispatched; Java supplies their explicit operation contract directly to
`codex_proxy/metrics_proxy.py` through the shared embedded Python runtime.

Default execution mode: `cli`
Default comparison mode: `off`

Shared agent model settings are the `model` and `reasoning_effort` values in
`codex_proxy/config.ini`. They apply to both execution modes. A Desktop
subagent must be spawned with both values explicitly; do not inherit the
orchestrator's model or reasoning effort. The CLI proxy reads the same values
programmatically.

A particular operation may explicitly override `model`, `reasoning_effort`, or
both. Resolve each omitted value independently from `codex_proxy/config.ini`,
then use the same resolved values for either execution mode. An override
applies only to that operation and must not modify the shared config.

The caller may explicitly choose `desktop` or `cli` for a particular agent
operation. An explicit per-operation choice overrides the default for that
operation only. If the caller does not specify a mode, use the default above.

The caller may explicitly enable comparison mode for a particular operation.
When comparison mode is enabled:

1. Assign one unique `operation_id` to the logical operation.
2. Execute the same instruction, input, contexts, and output schema once with
   `desktop` and once with `cli`, using the shared model settings for both.
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

  For a continued target, retain the thread ID returned by its first CLI turn
  and pass it as `--thread-id` on every later turn. This resumes the same
  persisted CLI session. Do not resend the static instruction and contexts;
  the proxy retains them in the target history and packages only the new input.

  Invoke the proxy once through a finite, non-interactive UTF-8 stdin pipe.
  Do not use an interactive PTY and do not create a temporary input file. Codex
  CLI requires network access, so from Desktop request network-capable execution
  on the first attempt; do not first probe the same invocation in the restricted
  workspace sandbox. Reuse a previously approved narrow proxy command when one
  is available instead of requesting approval for every operation.

The caller owns result handling and persistence. Desktop-owned operations must
not bypass this dispatcher. If the selected execution mode cannot preserve a
target that the owning orchestrator requires to be reused, stop instead of
silently creating a replacement agent.

When PowerShell supplies an in-memory payload on Python stdin, use
`common/utf8_stdin.md` for its UTF-8 transport. This does not change the selected
execution mode or the owning command.
