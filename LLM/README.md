# LLM

Experimental Java module (`com.seeker.llm`) for calling language models through
Spring AI. The first use case is finding the useful boundaries of vacancy text
after the existing HTML cleaner, without rewriting the retained text.

Start with native Windows Ollama and `qwen2.5:0.5b` to verify the integration.
Later, move inference to Oracle Cloud to free RAM on the 16 GB workstation.
JobSeeker and its Java client remain local.

This directory currently contains implementation instructions, not working code:

- [ARCHITECTURE.md](ARCHITECTURE.md): agreed decisions and integration context.
- [PLAN.md](PLAN.md): staged implementation and review checkpoints.
