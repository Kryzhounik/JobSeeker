package com.seeker.easyreasoning.llm;

import com.seeker.easyreasoning.EasyReasoningService;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.util.Assert;

import java.util.List;

/** Synchronous text generation. Callers are responsible for running off the UI thread. */
final class OllamaService implements EasyReasoningService {
    private final ChatModel chatModel;
    private final OllamaApi ollamaApi;
    private boolean used;

    OllamaService(ChatModel chatModel, OllamaApi ollamaApi) {
        this.chatModel = chatModel;
        this.ollamaApi = ollamaApi;
    }

    /** Returns the model's text unchanged; request failures propagate to the caller. */
    @Override
    public String generate(String prompt) {
        Assert.hasText(prompt, "prompt must not be blank");
        used = true;
        String response = chatModel.call(prompt);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned no text");
        }
        return response;
    }

    /** Unloads this session's model; an unused service needs no running Ollama. */
    @Override
    public void close() {
        if (used) {
            used = false;
            ollamaApi.chat(OllamaApi.ChatRequest.builder(chatModel.getOptions().getModel())
                    .messages(List.of()).stream(false).keepAlive("0").build());
        }
    }
}
