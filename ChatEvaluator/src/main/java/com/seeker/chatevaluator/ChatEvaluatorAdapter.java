package com.seeker.chatevaluator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/** Command declarations and API delegation; CliRunner owns parsing and output. */
@Command(name = "chat-evaluator")
public final class ChatEvaluatorAdapter {
    private final ChatEvaluatorApi api;

    public ChatEvaluatorAdapter(ChatEvaluatorApi api) {
        this.api = api;
    }

    @Command(name = "evaluate")
    public String evaluate(@Parameters(index = "0", arity = "1", paramLabel = "URL") String url)
            throws Exception {
        return api.evaluate(url);
    }
}
