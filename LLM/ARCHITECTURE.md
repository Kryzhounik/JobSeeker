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
  is enabled. The experimental step is opt-in; moving inference should require
  configuration changes, not a second Java implementation.

## Boundary Detection, Not Rewriting

The existing HTML cleaner remains unchanged. The additional step identifies
leading introductory material and trailing boilerplate that the script left
around the actual vacancy description. It must not edit the middle of the text.

The model returns boundaries, not a rewritten vacancy. Application code takes
the retained fragment from the original script-cleaned text. Do not summarize,
translate, paraphrase, or remove individual passages inside that fragment.
The exact boundary representation is an implementation detail, but the retained
text must be an unchanged contiguous substring of the input.

Keep the script-cleaned input and candidate result available for comparison.
When disabled, the existing processing path remains unchanged. Local experiments
first establish that the integration works; quality determines later whether
the optional step is useful for normal processing.

## Existing Integration Points

- `Driver/collector/extract_linkedin_readable_text_v2.py`: current HTML cleaner.
- `Driver/collector/java_linkedin/python_bridge.py::process_html`: invokes the
  cleaner, saves readable text, and applies the content filter in one operation.
- `Driver/db/readable_text.py`: persistence and loading of readable text.
- `Driver/analyzer/job_facts/load_input.py`: consumes readable text for analysis.
- `Driver/api/src/main/java/com/seeker/api/DriverApi.java`: current application
  facade and component construction.

The cleaner boundary is currently in Python, not a standalone Java service.
Integrate the Java LLM service after that cleaner through the smallest necessary
adaptation of the existing pipeline; do not move filtering or persistence into
LLM merely to connect it. Do not replace the current Codex agent operations.

## Cloud Boundary

Select the VM and larger model before provisioning, with an explicit resource
budget. Use Terraform for infrastructure and OCI CLI for authentication and
operational checks. Protect the remote Ollama endpoint rather than exposing an
unauthenticated inference API publicly. Credentials, private keys, and Terraform
state must not enter Git or application logs.
