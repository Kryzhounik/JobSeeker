# EasyReasoning Architecture

## Gateway Role

EasyReasoning is a gateway to models for inexpensive tasks. Preserve the stable
`String generate(String prompt)` API and matching CLI operation. Configuration
will select the execution backend and its model/options; callers do not select
providers in prompts or depend on their transport APIs. Currently only Ollama
is implemented; local and remote Ollama are deployment choices for that backend.
A future Codex backend, such as a light Luna profile, will reuse the existing
`com.seeker.codex.CodexClient` from `Tools` and `Driver/codex_proxy`. That transport
already returns the final text, handles usage limits, and supports model and
reasoning overrides through the proxy. EasyReasoning will supply its configured
options and prompt through a thin integration; do not build another Codex client
or introduce a separate SDK/CLI transport. This integration is not assigned yet.

Keep backend-specific requests and lifecycle behavior behind the gateway.
Vacancy cleanup is an application operation inside EasyReasoning over
`generate`; its prompting, line numbering, parsing and slicing must not become
responsibilities of each backend. Preserve independent requests without shared
chat history. Backend choice is explicit configuration, not automatic routing
or fallback. Do not build a provider registry, placeholder Codex configuration
or adapter infrastructure before the second backend is assigned.

The gateway's usefulness remains an experimental question. If self-hosted models
prove unusable for the intended tasks, reassess its value over calling the shared
Codex client directly. Failure of one small model does not rule out a later trial
with a larger model; retain a simple reusable boundary without assuming that
abstraction alone justifies the project.

## Module and Packages

The module is `EasyReasoning/`, Maven artifact `com.seeker:easy-reasoning`,
and base package `com.seeker.easyreasoning`. Keep the
generic gateway API, CLI and application operations outside provider packages.
Put all code specific to free/self-hosted model inference, currently Spring AI
and Ollama configuration, requests and model lifecycle, in
`com.seeker.easyreasoning.llm`. A future Codex integration reuses `Tools` rather
than moving or duplicating its shared client. `EasyReasoningService` is the
common text-generation and resource-close contract; the current
`llm.OllamaService` implementation owns all Ollama requests and model lifecycle.

## Application and Inference

- Use plain Spring Context in the existing Java application, without Spring
  Boot. Spring constructs and wires the LLM components; this does not require
  changing existing business logic or migrating unrelated components.
- Follow the module/package layout above and existing Maven conventions
  under `Driver/pom.xml`.
- The current Ollama backend uses Spring AI's `ChatModel` behind the application
  service. Spring AI is an implementation choice for this backend; a future
  Codex adapter need not implement `ChatModel`. Do not add a Spring web server.
- Ollama initially hosts inference natively on Windows. An Oracle Cloud VM is
  a later Ollama deployment option: only Ollama and model weights move there;
  Spring AI and JobSeeker remain local. Docker is out of scope.
- Configure the Ollama base URL, model name, and whether additional trimming
  is enabled. Trimming is disabled by default and must not require Ollama or a
  model when disabled. When enabled, model/request errors are failures: do not
  silently ignore them or substitute the original input. Moving inference should
  require configuration changes, not a second Ollama client implementation.

## Module API and CLI

In addition to the core `generate(prompt)` operation, expose text-to-text vacancy
cleanup, such as
`String cleanVacancy(String text)`, through a programmatic API and a CLI calling
the same operation. Line numbering, prompting, response parsing and source
slicing are internal to EasyReasoning; callers pass text and receive cleaned text.

Keep the CLI close to that text API. File I/O and combining sample pairs into a
single report can be handled by external scripts; do not require special input
files or batch configuration.

The CLI accepts plain text commands: `generate "text"` and `stop`, and prints
model responses as plain text. Quoted prompts may span multiple input lines.
Readiness and errors go to stderr; no JSON command/response protocol is required.

Use a long-lived Java process: initialize Spring and the selected backend once,
accept repeated operations through the CLI, and shut down through a separate
stop command. Operation calls reuse the initialized application. For local
Ollama inference, normal module shutdown must explicitly unload the configured
model from Ollama memory rather than leave it waiting for idle eviction. This is
the local Ollama backend's policy, not a universal model-unload contract.
Reconsider lifecycle separately for cloud Ollama and the future Codex backend.

## Boundary Detection, Not Rewriting

The existing HTML cleaner remains unchanged. The additional step identifies
leading introductory material and trailing boilerplate that the script left
around the actual vacancy description. It must not edit the middle of the text.

The model returns boundaries, not a rewritten vacancy. Application code takes
the retained fragment from the original script-cleaned text. Do not summarize,
translate, paraphrase, or remove individual passages inside that fragment.
Number the source lines from 1 in the model prompt and request only a JSON
object such as `{"from_line": 2, "to_line": 4}`, with both line numbers inclusive.
Numbering is prompt-only. Application code maps the selected lines to original character
offsets and extracts one unchanged contiguous substring, preserving whitespace
and line endings. This limits trimming to line boundaries.

Use simple temporary diagnostics for before/after inspection when needed;
comparison must not drive the processing architecture. First establish that
inference and the trimming concept work at all, including response time. Do not
solve hypothetical problems or add model-comparison infrastructure and experiment
bookkeeping before they are needed. Quality determines later whether the
optional step is useful for normal processing.

## Existing Integration Points

- `Driver/collector/extract_linkedin_readable_text_v2.py`: current HTML cleaner.
- `Driver/collector/java_linkedin/python_bridge.py::process_html`: invokes the
  cleaner, saves readable text, and applies the content filter in one operation.
- `Driver/db/readable_text.py`: persistence and loading of readable text.
- `Driver/analyzer/job_facts/load_input.py`: consumes readable text for analysis.
- `Driver/api/src/main/java/com/seeker/api/DriverApi.java`: current application
  facade and component construction.

The cleaner boundary is currently in Python, not a standalone Java service.
The flow is scripted HTML cleanup, optional LLM trimming, then normal downstream
processing with the selected text. Use the smallest necessary adaptation of the
existing pipeline; do not move filtering or persistence into LLM merely to
connect it. When trimming is disabled, the existing path remains unchanged.
Do not replace the current Codex agent operations.

## Cloud Boundary

Select the VM and larger model before provisioning, with an explicit resource
budget. Use Terraform for infrastructure and OCI CLI for authentication and
operational checks. Protect the remote Ollama endpoint rather than exposing an
unauthenticated inference API publicly. Credentials, private keys, and Terraform
state must not enter Git or application logs.
