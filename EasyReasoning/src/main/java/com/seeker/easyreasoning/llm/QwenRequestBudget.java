package com.seeker.easyreasoning.llm;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import com.seeker.easyreasoning.RequestTooLargeException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Tokenizes the standard Qwen2.5 chat template, without loading model weights. */
final class QwenRequestBudget implements AutoCloseable {
    static final String SYSTEM = "You are Qwen, created by Alibaba Cloud. You are a helpful assistant.";
    private final int contextLimit;
    private final Path tokenizerFile;
    private HuggingFaceTokenizer tokenizer;

    QwenRequestBudget(int contextLimit, Path tokenizerFile) {
        if (contextLimit <= 0) throw new IllegalArgumentException("Context limit must be positive");
        this.contextLimit = contextLimit;
        this.tokenizerFile = tokenizerFile;
    }

    int contextLimit() { return contextLimit; }

    synchronized int validate(String prompt) {
        int response = Math.max(count("NO SUBSTANTIVE DIFFERENCES"), count("SUBSTANTIVE DIFFERENCES")) + 1;
        int request = count(render(prompt));
        if ((long) request + response > contextLimit) {
            throw new RequestTooLargeException(request, contextLimit, response);
        }
        return response;
    }

    static String render(String prompt) {
        return "<|im_start|>system\n" + SYSTEM + "<|im_end|>\n"
                + "<|im_start|>user\n" + prompt + "<|im_end|>\n<|im_start|>assistant\n";
    }

    private int count(String text) {
        if (tokenizer == null) {
            try (var input = Files.newInputStream(tokenizerFile)) {
                tokenizer = HuggingFaceTokenizer.newInstance(input,
                        Map.of("truncation", "false", "padding", "false", "addSpecialTokens", "false"));
            } catch (IOException error) {
                throw new UncheckedIOException("Cannot load tokenizer: " + tokenizerFile
                        + ". Install the matching tokenizer together with the selected model; see EasyReasoning/README.md.", error);
            }
        }
        return tokenizer.encode(text, false, false).getIds().length;
    }

    @Override public synchronized void close() {
        if (tokenizer != null) { tokenizer.close(); tokenizer = null; }
    }
}
