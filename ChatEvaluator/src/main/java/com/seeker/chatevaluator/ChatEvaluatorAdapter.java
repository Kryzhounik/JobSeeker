package com.seeker.chatevaluator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/** Command declarations and API delegation; CliRunner owns parsing and output. */
@Command(name = "chat-evaluator")
public final class ChatEvaluatorAdapter {
    private final ChatEvaluatorApi api;
    @Spec private CommandSpec spec;

    public ChatEvaluatorAdapter(ChatEvaluatorApi api) {
        this.api = api;
    }

    @Command(name = "evaluate")
    public String evaluate(@Parameters(index = "0", arity = "1", paramLabel = "URL") String url)
            throws Exception {
        try { return api.evaluate(url); }
        catch (ChatEvaluatorApi.InvalidUrlException error) {
            throw new ParameterException(spec.commandLine(), error.getMessage());
        }
    }
}
