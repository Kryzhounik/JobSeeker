# EasyReasoning Experiments and Verification Records

These are historical observations and verification reports, not new test runs,
approved architectural decisions, or implementation assignments. Research tasks
are in [PLAN.md](PLAN.md). Generated inputs, outputs and raw verification
artifacts remain in ignored local data directories.

## Preliminary Boundary-Trimming Observations — 2026-10-25

The implementation agent's checkpoint 5 report described a real
`qwen2.5:0.5b` attempt that returned usable boundaries inside a JSON code block
with extra description fields. A repeat attempt generated an excessively long
continuation and was stopped manually. Ollama's stop command did not promptly
release the active runner, so the test server was restarted and its loaded-model
list was verified empty.

The cause, frequency and reproducibility are unknown. These isolated checks do
not establish that this is an unavoidable system limitation or that the small
model is useful for normal collection. Checkpoint 6 investigates whether prompt,
model or inference settings can eliminate the behavior. Protective limits,
timeouts or recovery mechanisms are not assigned. Handling should be agreed
separately only if the behavior is accepted as a remaining system limitation.

## Historical Verification Reports

The reports below were moved from README. Counts and timings refer to those
past runs, not the current checkout's verification status.

Result: 10 EasyReasoning tests passed after the plain-text CLI update; the
previous Java integration check passed 5 Driver API tests. The dependency tree contains
both Jackson mapper generations with `jackson-annotations` 2.21 and no Spring
Boot dependencies.

Checkpoint 3 was verified on 2026-10-24 with native Ollama 0.40.10 and
`qwen2.5:0.5b`. The executable JAR was started from outside the project root.
Two real requests were served by the same Java process: approximately 34.4 s
for the first request including model loading, then 54 ms for the second.
The model was present in `/api/ps` after each request. Explicit `stop` returned
success, the process exited 0, and `/api/ps` returned an empty model list.
Generated verification evidence is kept outside tracked source in
`.tools/llm-local-verification.json`.

Direct JAR launch was also checked without encoding flags or a PowerShell
launcher. Two UTF-8 stdin commands containing Cyrillic text and multiline
content reached a local echo HTTP stub unchanged and returned unchanged in
the CLI's UTF-8 stdout, including LF/CRLF and quoted text. The process handled
both commands and `stop`, unloaded the model on the stub, and exited 0.
Evidence is in `.tools/llm-direct-utf8-verification.json`.

The small model produced an incorrect arithmetic answer in this check. This
checkpoint establishes inference and lifecycle integration, not output quality.

Checkpoint 4 was verified on 2026-10-24 after the rename. The 14 automated tests
passed using the new Maven artifact. JavaFX compiled with the renamed dependency
and its Spring lifecycle smoke check passed. The standalone
`EasyReasoning/easy-reasoning.jar` preserved Cyrillic, LF/CRLF and quoted text in
two commands without encoding flags. Two real Ollama requests ran in one process
from outside the project root; explicit stop unloaded the model and exited 0.
Generated evidence is in `.tools/easy-reasoning-direct-utf8-verification.json`
and `.tools/easy-reasoning-local-verification.json`.

The plain-text CLI update passed 10 module tests and direct executable-JAR checks.
Cyrillic, multiline prompts and embedded quotes reached an echo server unchanged.
Two plain-text commands also returned real Ollama answers in one process, and
`stop` unloaded the model. Evidence is in `.tools/easy-reasoning-text-utf8.json`
and `.tools/easy-reasoning-text-real.json`.

Checkpoint 5 verification on 2026-10-25 passed 17 EasyReasoning tests, 5 Driver
API tests, 6 Python bridge tests and the native JPy gateway integration test.
The native check used a temporary SQLite database and a controlled HTTP model:
both disabled and enabled paths worked, the selected text was persisted, and
closing the collector unloaded the model. No browser or LinkedIn request was
needed. Tests also cover model/parse failures, invalid line numbers, unchanged
mixed line endings, before/after files, and the plain-text cleanup CLI command.

Checkpoint 6 was repeated on 2026-10-25 after correcting the initial sample:
three initial records had no main vacancy description, so the first report is
not the accepted experiment. The corrected sample contains 10 inspected CLEANED
vacancy descriptions, 1603–12890 characters each, with duties and qualifications.
The same real cleanVacancy API, model and inference settings were retained.
Seven calls returned source fragments, one failed on invalid JSON, and two
(DB 1454 and 1342) did not complete within 120 seconds each. The external
experiment script terminated those calls and restarted only its own processes.
Returned fragments lost useful vacancy content; technical success does not
mean acceptable trimming. Completed calls took 0.50–3.67 seconds.
Sampled working-set peaks were 135.5 MB Java and 655.2 MB Ollama with runner.
The corrected single report with full input/output pairs, errors, raw model
responses, timings and resource measurements is:
`Data/easy-reasoning/experiments/20261025-151711/report.md`.
The user will submit the report to ChatGPT independently. No collector rerun,
integration checks, inference tuning or production code changes were performed.

On 2026-10-25 the same 10 inspected vacancy texts and external trimming
instruction were also evaluated with `gpt-6-luna`, reasoning effort `low`,
through the existing `Driver/codex_proxy` CLI transport. Each input used a new
session (10 distinct thread IDs) without conversation history. All 10 calls
returned valid boundaries; manual inspection found the main descriptions,
duties and qualifications preserved in all 10 fragments. Some internal LinkedIn
UI text remained, consistent with the single contiguous-fragment contract.
This establishes that the task is feasible with the current instruction on
this sample, while Qwen 0.5b was unsuitable in the earlier run. The proxy also
enforced a JSON response schema, unlike Ollama, so JSON correctness alone is
not a controlled model comparison.
Proxy accounting recorded 154053 input tokens, including 14208 cached input,
and 210 output tokens across the 10 calls. These figures include CLI overhead;
cached input is a subset of input. Total CLI invocation time was 99.83 seconds,
median 4.58 seconds. Four preliminary app-subagent calls before the user's
proxy clarification are recorded separately and excluded from these totals;
their token usage was unavailable. Metrics were written to a separate
experiment SQLite database, leaving the main jobs database unchanged.
Full inputs, outputs, boundaries, per-call usage and comparison are in:
`Data/easy-reasoning/experiments/luna-proxy-20261025-172048/report.md`.
No Codex backend was added to EasyReasoning and no shared proxy code or
configuration was changed.
