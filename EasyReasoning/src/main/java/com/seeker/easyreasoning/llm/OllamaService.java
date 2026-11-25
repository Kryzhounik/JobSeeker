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
    private final OllamaRuntime runtime;
    private boolean used;
    private final QwenRequestBudget budget;

    OllamaService(ChatModel chatModel, OllamaApi ollamaApi, OllamaRuntime runtime, QwenRequestBudget budget) {
        this.budget = budget;
        this.chatModel = chatModel;
        this.ollamaApi = ollamaApi;
        this.runtime = runtime;
    }

    /** Returns the model's text unchanged; request failures propagate to the caller. */
    @Override
    public synchronized String generate(String prompt) {
        Assert.hasText(prompt, "prompt must not be blank");
        runtime.ensureRunning();
        used = true;
        String response = chatModel.call(prompt);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("LLM returned no text");
        }
        return response;
    }

    @Override
    public synchronized String generateComparison(String prompt) {
        String model = chatModel.getOptions().getModel();
        if (!model.matches("qwen2\\.5:(0\\.5b|1\\.5b|3b|7b|14b|32b|72b)")) {
            throw new IllegalStateException("Comparison tokenizer requires standard qwen2.5: models");
        }
        int responseTokens = budget.validate(prompt);
        var options = org.springframework.ai.ollama.api.OllamaChatOptions.builder().model(model)
                .numCtx(budget.contextLimit()).numPredict(responseTokens).keepAlive("-1m").build();
        var request = new org.springframework.ai.chat.prompt.Prompt(List.of(
                new org.springframework.ai.chat.messages.SystemMessage(QwenRequestBudget.SYSTEM),
                new org.springframework.ai.chat.messages.UserMessage(prompt)), options);
        runtime.ensureRunning();
        used = true;
        String response = chatModel.call(request).getResult().getOutput().getText();
        if (response == null || response.isBlank()) throw new IllegalStateException("LLM returned no text");
        return response;
    }

    /** Unloads this session's model; an unused service needs no running Ollama. */
    @Override
    public synchronized void close() {
        try {
            if (used) {
                used = false;
                ollamaApi.chat(OllamaApi.ChatRequest.builder(chatModel.getOptions().getModel())
                        .messages(List.of()).stream(false).keepAlive("0").build());
            }
        } finally {
            runtime.close();
        }
    }
}
