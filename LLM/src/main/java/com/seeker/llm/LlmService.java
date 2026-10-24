package com.seeker.llm;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.util.Assert;

/** Synchronous text generation. Callers are responsible for running off the UI thread. */
public final class LlmService {
    private final ChatModel chatModel;

    public LlmService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /** Returns the model's text unchanged; request failures propagate to the caller. */
    public String generate(String prompt) {
        Assert.hasText(prompt, "prompt must not be blank");
        String response = chatModel.call(prompt);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned no text");
        }
        return response;
    }
}
