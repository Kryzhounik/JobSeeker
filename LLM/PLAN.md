# Implementation Plan

Follow [ARCHITECTURE.md](ARCHITECTURE.md). These are formal review checkpoints:
complete one numbered item, report the changes and verification results, then
stop. Wait for the user's approval before starting the next item. Implementation
and independent review are performed by different agents.

Item 1 is complete. Do not start any remaining item without the user's explicit
go-ahead. Discussion or documentation updates do not authorize implementation.
The LLM supervisor coordinates assignments and review; that role alone is not
an implementation assignment. Later items may be reconsidered or never reached
if the concept proves impractical.

1. **Spring Context.** Add the LLM Maven module and plain Spring Context wiring
   to the existing Java application. Verify context initialization and that
   existing workflows remain available. The model client belongs to the next step.

2. **Spring AI client.** Configure `ChatModel` and a simple application service
   for Ollama, with external connection/model settings. Verify the client wiring;
   end-to-end inference follows after the local server is installed. Use the
   [manual configuration](https://docs.spring.io/spring-ai/reference/api/chat/ollama-chat.html)
   path, not Boot auto-configuration.

3. **Local inference.** Install native Windows Ollama and
   [`qwen2.5:0.5b`](https://ollama.com/library/qwen2.5:0.5b). Make a real request
   through the Java service and verify the response. This model is for checking
   the integration on the memory-constrained workstation, not a verdict on quality.

4. **Optional boundary trimming.** Add an explicit configuration switch,
   disabled by default, for LLM postprocessing immediately after the existing
   HTML cleaner. When enabled, detect boundaries, extract the unchanged source
   fragment, and continue through the existing downstream pipeline. When disabled,
   the existing path must work without Ollama or a model installed. Errors from
   the enabled LLM step must be reported as failures, not silently ignored or
   replaced with the original text. Retain before/after text for experiments
   without redesigning the processing flow for comparison. Verify both enabled
   and disabled paths; do not rewrite the cleaner.

5. **Local experiments.** Exercise configuration, request/response handling,
   and real vacancy text through the integrated path. Check that the retained
   text is an exact source fragment. Record sample inputs, boundaries, outputs,
   and timings; quality is secondary at this checkpoint. Keep generated data
   outside tracked source files.

6. **Oracle Cloud inference.** Agree on VM resources and a larger model, then
   provision the inference server using Terraform and OCI CLI, without Docker.
   Verify protected access and a request from the same local Java service after
   changing its configuration. Do not deploy Spring AI or JobSeeker to the VM.

7. **Quality and usefulness.** Compare the existing cleaner's output with the
   additional trimming on the same vacancies. Assess boundary errors, lost
   requirements, preserved wording, token reduction, and latency. Check the effect
   on downstream analysis before recommending normal use; do not enable it by
   default solely because the integration works.
