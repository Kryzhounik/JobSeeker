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
Existing lower-module services and standalone CLIs do not use the context.

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

The client is not yet connected to vacancy processing. Local installation, real
inference and the standalone long-lived CLI for `generate(prompt)` are checkpoint
3. Vacancy cleanup, its CLI command and collector integration are checkpoint 4.

## Verification

From the repository root:

```powershell
mvn -f LLM/pom.xml test
```

Tests cover default wiring without Ollama, file-configured HTTP requests and
unchanged response text using a local stub server, environment variable overrides,
and provider failure propagation. Connection failures and HTTP 503 responses are
checked for prompt failure without automatic retries. They do not install or call a real model.

Checkpoint 2 verification also passed with the application dependency graph:

```powershell
mvn -f Driver/pom.xml -pl api -am '-Dtest=LlmConfigurationTest,DriverConfigurationTest,MainTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Result: 5 LLM tests and 5 Driver API tests passed. The dependency tree contains
both Jackson mapper generations with `jackson-annotations` 2.21 and no Spring
Boot dependencies.

Implementation instructions:

- [ARCHITECTURE.md](ARCHITECTURE.md): agreed decisions and integration context.
- [PLAN.md](PLAN.md): staged implementation and review checkpoints.
