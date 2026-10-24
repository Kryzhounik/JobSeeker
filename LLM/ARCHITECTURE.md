# Architecture

## Application and Inference

- Use plain Spring Context in the existing Java application, without Spring
  Boot. Spring constructs and wires the LLM components; this does not require
  changing existing business logic or migrating unrelated components.
- Put the Java integration in the `LLM` module, package `com.seeker.llm`, using
  the existing Maven conventions under `Driver/pom.xml`.
- Use Spring AI's `ChatModel` with an Ollama implementation behind a simple
  application service. Do not introduce a separate Spring web server.
- Ollama hosts inference, initially natively on Windows, later on an Oracle
  Cloud VM. Only Ollama and model weights move to the cloud; Spring AI and
  JobSeeker remain on the workstation. Docker is out of scope.
- Configure the Ollama base URL, model name, and whether additional trimming
  is enabled. Trimming is disabled by default and must not require Ollama or a
  model when disabled. When enabled, model/request errors are failures: do not
  silently ignore them or substitute the original input. Moving inference should
  require configuration changes, not a second Java implementation.

## Module API and CLI

Expose standalone text-to-text vacancy cleanup, such as
`String cleanVacancy(String text)`, through a programmatic API and a CLI calling
the same operation. Line numbering, prompting, response parsing and source
slicing are internal to LLM; callers pass text and receive cleaned text.

Keep the CLI close to that text API. File I/O and combining sample pairs into a
single report can be handled by external scripts; do not require special input
files or batch configuration.

Use a long-lived Java process: initialize Spring and the model client once,
accept repeated operations through the CLI, and shut down through a separate
stop command. Operation calls reuse the initialized application. For local
inference, normal module shutdown must explicitly unload the configured model
from Ollama memory rather than leave it waiting for idle eviction. Reconsider
the model lifecycle policy separately when moving inference to the cloud.

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
