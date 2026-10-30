# EasyReasoning

**Current status:** the module is currently unused. Available models did not
provide a substantial, consistent improvement in vacancy trimming without losing
useful content; script-based cleanup achieved better quality. Its gateway purpose
remains; revisit it for other tasks or a more powerful model.

Java gateway to models for inexpensive text and reasoning tasks. Its core API is
`String generate(String prompt)`. The intended configuration selects the backend
and model; callers use the same programmatic API and CLI whichever backend runs
the request.

Only the Ollama backend is implemented today, using Spring AI. It can address a
local or remote Ollama server. A future Codex backend (for example, a light Luna
profile) will reuse `Tools`' shared `com.seeker.codex.CodexClient` and the existing
`Driver/codex_proxy`, which already handle invocation, final results, usage limits
and model/reasoning overrides. The remaining work is connecting that client to
the gateway's prompt and configuration; it is not implemented or assigned yet.
Backend selection is explicit configuration, without automatic routing or
silent fallback.

Vacancy boundary trimming is the first application of this gateway. Its prompt,
line numbering and extraction belong to a task-specific operation over
`generate`, rather than the backend implementation. Email analysis remains only
a possible future application.

If self-hosted models prove unsuitable, the gateway's value over direct shared
CodexClient calls must be reassessed. Failure of the first small model still
leaves room to try larger models later through the same simple boundary.

The module lives in `EasyReasoning/`, with Maven artifact
`com.seeker:easy-reasoning` and base package `com.seeker.easyreasoning`.
`EasyReasoningService` defines the common `generate(prompt)` and resource-close
contract. The CLI and application configuration live alongside that API.
All Spring AI/Ollama settings, calls, and model unloading are implemented in
`com.seeker.easyreasoning.llm`. There is no provider registry or backend selector.

The initial setup uses native Windows Ollama and `qwen2.5:7b`. Hosting Ollama
on Oracle Cloud is a later deployment option to free local RAM; JobSeeker and
the Java gateway remain local.

The Maven artifact `com.seeker:easy-reasoning:1.0-SNAPSHOT` provides
`EasyReasoningConfiguration`,
registered through `DriverConfiguration` in the Java API. It imports the current
`llm.OllamaConfiguration`, which manually constructs Spring AI 2.0.2's Ollama
`ChatModel` and exposes its implementation as `EasyReasoningService`. There is no
Spring Boot auto-configuration or web server. Spring Framework versions remain
managed in `Driver/pom.xml`; the Spring AI BOM is local to `EasyReasoning/pom.xml`.
The parent also aligns `jackson-annotations` to 2.21, shared by Spring AI's
Jackson 3 mapper and the existing Jackson 2.18.3 consumers.

JavaFX creates the context in `SeekerApplication.init()`, obtains `DriverApi`
from it, and closes it in `stop()` (with a JVM shutdown hook as a fallback).
Existing lower-module services and their CLIs do not use that context. The
EasyReasoning CLI creates its own context containing only gateway components.

## Client configuration

Edit `EasyReasoning/easy-reasoning.properties` to change the server address or
model, then restart the application. The file is loaded relative to the project root supplied by the
application, independently of the working directory. No command-line arguments
or Windows environment settings are needed.

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.model=qwen2.5:7b
```

If the file or a setting is absent, the defaults below apply. JVM system
properties and environment variables remain optional overrides, in that order
of precedence, above the file. Unreadable or malformed files fail initialization.
No Boot `application.properties` loading is used.

| JVM system property | Environment variable | Default |
| --- | --- | --- |
| `spring.ai.ollama.base-url` | `SPRING_AI_OLLAMA_BASE_URL` | `http://localhost:11434` |
| `spring.ai.ollama.chat.model` | `SPRING_AI_OLLAMA_CHAT_MODEL` | `qwen2.5:7b` |

The current API is `String generate(String prompt)`: send an arbitrary prompt
and receive the model's text response. Vacancy cleanup is a later operation.
Obtain `com.seeker.easyreasoning.EasyReasoningService` from the existing context
and call `generate(prompt)` off the UI thread. It returns the model's text
unchanged, propagates request errors,
and rejects empty responses. Automatic Spring AI retries are disabled: a failed
request is reported after the first attempt. Context initialization makes no inference requests
and never pulls models, so it works without Ollama installed. Only an explicit
`generate` call needs a running server and an already installed model.

For local inference, generated models remain loaded between operations
(`keep_alive=-1m`). Closing a used Ollama-backed service explicitly unloads the configured
model with an empty Ollama chat request and `keep_alive=0`, including after a
failed generation attempt that may have loaded the model. Spring owns the
service's close lifecycle. An unused context does not contact Ollama on close.
The CLI also closes the service explicitly so unload failures cause a nonzero
exit rather than a successful stop acknowledgement. This is the local lifecycle
policy of the current Ollama backend, not a requirement that every future
backend manages local model weights. Reconsider it before cloud deployment.

## Optional vacancy cleanup

`VacancyCleaner.cleanVacancy(String text)` identifies one contiguous vacancy
fragment through the common `generate(prompt)` API. `EasyReasoningApplication`
owns a standalone Spring context and exposes both `generate` and `cleanVacancy`
for callers that need an initialized gateway. Reuse it across operations and
close it afterwards; callers already owning a Spring context can use its beans.

Set `vacancy.trimming.enabled=true` in `EasyReasoning/easy-reasoning.properties`
to enable cleanup. The default is `false`: the operation returns the original
string without a model request or diagnostic files. The setting belongs to
EasyReasoning. Collector code always invokes cleanup and does not inspect it.
Settings are read when the gateway is created (at CLI startup or collection start).

When enabled, EasyReasoning numbers source lines only in the prompt and asks for
boundaries using the UTF-8 instructions in `EasyReasoning/prompts/clean-vacancy.md`.
Edit that file without rebuilding or restarting: it is read for each nonempty
cleanup call. The numbered source text is appended automatically. When cleanup
is disabled, the prompt file is not read.
The model returns
integer, 1-based inclusive `from_line` and `to_line` boundaries. EasyReasoning validates the
response and slices the original string at the selected lines' character offsets.
Line terminators belonging to the selected lines are retained, including mixed
LF/CRLF/CR, along with the original wording and whitespace. There is no rewriting
or deletion of passages inside the fragment. Empty input needs no inference.
Malformed responses, invalid boundaries, model errors and diagnostic write
failures propagate; there is no fallback to the original text when enabled.
The parser accepts a JSON object or one JSON code block, using only the boundary
fields. Any additional model-generated description is ignored, never used as text.

Each enabled attempt saves a UUID-prefixed `.before.txt` and `.response.txt`;
successful cleanup also saves `.after.txt`, under `Data/easy-reasoning/trimming/`.
The files use UTF-8 and preserve source line endings. A failed model request may
leave only the before file. These are local inspection files, ignored by Git.

The Java collector owns one `EasyReasoningApplication` per collection and passes
it to `python_bridge.process_html`. Python invokes `cleanVacancy` immediately
after the existing HTML cleaner, then persists and content-filters the selected
text through the existing pipeline. The HTML cleaner is unchanged. Closing the
collector gateway closes EasyReasoning and unloads a used local model. Failed
cleanup follows the collector's existing per-vacancy failure reporting and does
not persist/filter the original readable text instead.

## Standalone CLI

Build from the repository root (Java 21 or later):

```powershell
mvn -f Driver/pom.xml -pl ../EasyReasoning -am package
```

The thin `EasyReasoning/easy-reasoning.jar` uses the copied dependencies in `libs/`. Start it with:

```powershell
java -jar EasyReasoning/easy-reasoning.jar
```

Use Java 21 or later available as `java` on PATH, or invoke `bin/java.exe` from
your JDK directly. The CLI finds the project root from its own JAR location;
the working directory is irrelevant once the JAR path is resolved. An optional
positional project-root argument is available.

One process initializes Spring and the model client once. Enter ordinary text
commands after the readiness message:

```text
generate "Привет, как дела?"
generate Reply with exactly HELLO
clean-vacancy "Navigation
Java Developer
Responsibilities: build Java services.
Footer"
stop
```

The Java CLI explicitly reads and writes UTF-8; it does not depend on a launcher
setting `file.encoding`. Scripts that pipe commands into it must also send UTF-8
bytes. Double quotes around the prompt are optional. A quoted prompt can span
multiple input lines; finish it with the closing quote:

```text
generate "Первая строка
Вторая строка"
```

Inside double quotes, `\"` inserts a quote and `\\` inserts a backslash. Other
backslashes are literal. Multiline input uses LF between lines.

Each generation invokes the same `EasyReasoningService.generate(prompt)` API.
`clean-vacancy` invokes the configured `cleanVacancy(text)` operation.
The model's answer is printed directly to stdout, followed by a line ending if
needed. Requests are independent, without accumulating chat history. Readiness,
errors and shutdown messages are plain text on stderr. After a command or
request error the process remains available for the next command.
If any command or generation failed during the session, the process exits 1
on `stop` or end-of-input, even if subsequent requests succeeded. A session
without errors exits 0. Startup and backend shutdown failures also exit 1.

`stop` unloads the used model and closes the application, then prints `Stopped.`
on stderr. End-of-input performs the same normal cleanup. Do not
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
& .\.tools\ollama\ollama.exe pull qwen2.5:7b
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
mvn -f EasyReasoning/pom.xml test
```

Tests cover default wiring without Ollama, file-configured HTTP requests and
unchanged response text using a local stub server, environment variable overrides,
and provider failure propagation. Connection failures and HTTP 503 responses are
checked for prompt failure without automatic retries. CLI tests cover repeated
operations, independent prompts, command/request errors, stop/EOF unloading,
and reporting unload failure. These automated tests do not call a real model.

Java integration verification:

```powershell
mvn -f Driver/pom.xml -pl api -am '-Dtest=VacancyCleanerTest,OllamaConfigurationTest,EasyReasoningCliTest,DriverConfigurationTest,MainTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Experiment observations and historical verification reports are in
[EXPERIMENTS.md](EXPERIMENTS.md). Research tasks are in [PLAN.md](PLAN.md).

Implementation instructions:

- [ARCHITECTURE.md](ARCHITECTURE.md): agreed decisions and integration context.
- [PLAN.md](PLAN.md): staged implementation and review checkpoints.
- [EXPERIMENTS.md](EXPERIMENTS.md): observations and historical verification reports.
