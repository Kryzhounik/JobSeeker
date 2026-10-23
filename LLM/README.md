# LLM

Experimental Java module (`com.seeker.llm`) for calling language models through
Spring AI. The first use case is finding the useful boundaries of vacancy text
after the existing HTML cleaner, without rewriting the retained text.

Start with native Windows Ollama and `qwen2.5:0.5b` to verify the integration.
Later, move inference to Oracle Cloud to free RAM on the 16 GB workstation.
JobSeeker and its Java client remain local.

The Maven artifact `com.seeker:llm:1.0-SNAPSHOT` currently provides only
`LlmConfiguration`, registered through `DriverConfiguration` in the Java API.
Spring Context versions are managed in `Driver/pom.xml`. There is no model
client or inference call yet.

JavaFX creates the context in `SeekerApplication.init()`, obtains `DriverApi`
from it, and closes it in `stop()` (with a JVM shutdown hook as a fallback).
Existing lower-module services and standalone CLIs do not use the context.

Implementation instructions:

- [ARCHITECTURE.md](ARCHITECTURE.md): agreed decisions and integration context.
- [PLAN.md](PLAN.md): staged implementation and review checkpoints.
