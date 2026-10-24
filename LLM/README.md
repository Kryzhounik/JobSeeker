# LLM

Experimental Java module (`com.seeker.llm`) for calling language models through
Spring AI. The first use case is finding the useful boundaries of vacancy text
after the existing HTML cleaner, without rewriting the retained text.

Start with native Windows Ollama and `qwen2.5:0.5b` to verify the integration.
Later, move inference to Oracle Cloud to free RAM on the 16 GB workstation.
JobSeeker and its Java client remain local.

The Maven artifact `com.seeker:llm:1.0-SNAPSHOT` provides `LlmConfiguration`,
registered through `DriverConfiguration` in the Java API. It manually constructs
Spring AI 2.0.2's Ollama `ChatModel` and a synchronous `LlmService`. There is no
Spring Boot auto-configuration or web server. Spring Framework versions remain
managed in `Driver/pom.xml`; the Spring AI BOM is local to `LLM/pom.xml`.
The parent also aligns `jackson-annotations` to 2.21, shared by Spring AI's
Jackson 3 mapper and the existing Jackson 2.18.3 consumers.

JavaFX creates the context in `SeekerApplication.init()`, obtains `DriverApi`
from it, and closes it in `stop()` (with a JVM shutdown hook as a fallback).
Existing lower-module services and their CLIs do not use that context. The LLM
CLI creates its own context containing only LLM components.

## Client configuration

Edit `LLM/llm.properties` to change the server address or model, then restart the
application. The file is loaded relative to the project root supplied by the
application, independently of the working directory. No command-line arguments
or Windows environment settings are needed.

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen2.5:0.5b
```

If the file or a setting is absent, the defaults below apply. JVM system
properties and environment variables remain optional overrides, in that order
of precedence, above the file. Unreadable or malformed files fail initialization.
No Boot `application.properties` loading is used.

| JVM system property | Environment variable | Default |
| --- | --- | --- |
| `spring.ai.ollama.base-url` | `SPRING_AI_OLLAMA_BASE_URL` | `http://localhost:11434` |
| `spring.ai.ollama.chat.model` | `SPRING_AI_OLLAMA_CHAT_MODEL` | `qwen2.5:0.5b` |

The current API is `String generate(String prompt)`: send an arbitrary prompt
and receive the model's text response. Vacancy cleanup is a later operation.
Obtain `LlmService` from the existing context and call `generate(prompt)` off the
UI thread. It returns the model's text unchanged, propagates request errors,
and rejects empty responses. Automatic Spring AI retries are disabled: a failed
request is reported after the first attempt. Context initialization makes no inference requests
and never pulls models, so it works without Ollama installed. Only an explicit
`generate` call needs a running server and an already installed model.

For local inference, generated models remain loaded between operations
(`keep_alive=-1m`). Closing a used `LlmService` explicitly unloads the configured
model with an empty Ollama chat request and `keep_alive=0`, including after a
failed generation attempt that may have loaded the model. Spring owns the
service's close lifecycle. An unused context does not contact Ollama on close.
The CLI also closes the service explicitly so unload failures cause a nonzero
exit rather than a successful stop acknowledgement. This is the local lifecycle
policy; reconsider it before cloud deployment.

The client is not yet connected to vacancy processing. Vacancy cleanup, its
CLI command and collector integration are checkpoint 4.

## Standalone CLI

Build from the repository root (Java 21 or later):

```powershell
mvn -f Driver/pom.xml -pl ../LLM -am package
```

The thin `LLM/llm.jar` uses the copied dependencies in `libs/`. Start it with:

```powershell
java -jar LLM/llm.jar
```

Use Java 21 or later available as `java` on PATH, or invoke `bin/java.exe` from
your JDK directly. The CLI finds the project root from its own JAR location;
the working directory is irrelevant once the JAR path is resolved. An optional
positional project-root argument is available.

One process initializes Spring and the model client once and accepts UTF-8 JSON
objects, one per input line. After the `ready` reply, enter commands such as:

```json
{"command":"generate","prompt":"Reply with exactly HELLO"}
{"command":"generate","prompt":"Первая строка\nВторая строка"}
{"command":"stop"}
```

The Java CLI explicitly reads and writes UTF-8; it does not depend on a launcher
setting `file.encoding`. Scripts that pipe commands into it must also send UTF-8
bytes. Multiline prompt content is encoded as `\n` within the JSON command;
each command itself occupies one input line.

Each generation invokes the same `LlmService.generate(prompt)` API and returns
`{"status":"ok","text":"..."}`. Requests are independent, without accumulating
chat history. JSON escaping handles multiline text without special input files.
Malformed commands or failed requests return `{"status":"error","error":"..."}`;
the process remains available for the next command. Scripts must check each
reply's status, not just the process exit code.

`stop` unloads the used model and closes the application, then replies
`{"status":"stopped"}`. End-of-input performs the same normal cleanup. Do not
force-kill the process when normal model unloading is required. No success reply
is emitted if unloading fails; the CLI reports the failure on stderr and exits 1.
Stopping before any inference attempt works without an Ollama server.

## Native Windows Ollama

Use the official standalone Windows archive, extracted to `.tools/ollama/`.
This is a native installation without Docker, GUI dependencies, or changes to
the Windows PATH. See [Ollama's Windows instructions](https://docs.ollama.com/windows).
The archive and extracted files are ignored by Git.

Start the Ollama backend separately in the background:

```powershell
Start-Process .\.tools\ollama\ollama.exe -ArgumentList 'serve' -WindowStyle Hidden
```

Install the test model once:

```powershell
& .\.tools\ollama\ollama.exe pull qwen2.5:0.5b
```

Model weights use Ollama's default user directory (`$HOME/.ollama/models`).
The Java CLI's `stop` unloads the model; the backend remains available for the
next session. Check loaded models with:

```powershell
& .\.tools\ollama\ollama.exe ps
```

## Verification

From the repository root:

```powershell
mvn -f LLM/pom.xml test
```

Tests cover default wiring without Ollama, file-configured HTTP requests and
unchanged response text using a local stub server, environment variable overrides,
and provider failure propagation. Connection failures and HTTP 503 responses are
checked for prompt failure without automatic retries. CLI tests cover repeated
operations, independent prompts, command/request errors, stop/EOF unloading,
and reporting unload failure. These automated tests do not call a real model.

Checkpoint 2 verification also passed with the application dependency graph:

```powershell
mvn -f Driver/pom.xml -pl api -am '-Dtest=LlmConfigurationTest,LlmCliTest,DriverConfigurationTest,MainTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: 9 LLM tests and 5 Driver API tests passed. The dependency tree contains
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

Implementation instructions:

- [ARCHITECTURE.md](ARCHITECTURE.md): agreed decisions and integration context.
- [PLAN.md](PLAN.md): staged implementation and review checkpoints.
