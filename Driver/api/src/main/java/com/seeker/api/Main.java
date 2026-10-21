package com.seeker.api;

import com.seeker.chatevaluator.ChatEvaluatorRunner;

import java.util.Arrays;
import java.util.function.ToIntFunction;

/** Routes commands to module CLI handlers in the same JVM. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String[] args) {
        return execute(args,
                com.seeker.collector.linkedin.Main::execute,
                com.seeker.orchestrator.Main::execute,
                com.seeker.applier.Main::execute,
                ChatEvaluatorRunner::execute);
    }

    static int execute(
            String[] args,
            ToIntFunction<String[]> collector,
            ToIntFunction<String[]> orchestrator,
            ToIntFunction<String[]> applier,
            ToIntFunction<String[]> chatEvaluator
    ) {
        if (args.length == 0) {
            System.err.println("Expected a command: login, collect-batch, process-batch, "
                    + "process-urls, resume, job-facts, finish-analysis, apply, chat-eval");
            return 2;
        }
        return switch (args[0]) {
            case "login", "collect-batch" -> collector.applyAsInt(args);
            case "process-batch", "process-urls", "resume", "job-facts", "finish-analysis" ->
                    orchestrator.applyAsInt(args);
            case "apply" -> applier.applyAsInt(Arrays.copyOfRange(args, 1, args.length));
            case "chat-eval" -> chatEvaluator.applyAsInt(Arrays.copyOfRange(args, 1, args.length));
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield 2;
            }
        };
    }
}
