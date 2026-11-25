package com.seeker.api;

import com.seeker.applier.ApplierApi;
import com.seeker.applier.ApplierAdapter;
import com.seeker.chatevaluator.ChatEvaluatorApi;
import com.seeker.chatevaluator.ChatEvaluatorAdapter;
import com.seeker.tools.cli.CliRunner;
import java.nio.file.Path;
import java.util.stream.Stream;

import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

/** Routes commands to module CLI handlers in the same JVM. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String[] args) throws Exception {
        var chatEvaluator = new ChatEvaluatorAdapter(new ChatEvaluatorApi(
                Path.of(System.getProperty("seeker.projectRoot", "."))));
        var applier = new ApplierAdapter(new ApplierApi(
                Path.of(System.getProperty("seeker.projectRoot", "."))));
        return execute(args,
                () -> com.seeker.collector.core.CollectorCli.execute(args),
                () -> com.seeker.orchestrator.OrchestratorCli.execute(args),
                rest -> CliRunner.run(applier, ApplierAdapter.arguments(rest)),
                // Preserve chat-eval URL while using the same annotated command as the standalone CLI.
                urls -> CliRunner.run(chatEvaluator,
                        Stream.concat(Stream.of("evaluate"), Arrays.stream(urls)).toArray(String[]::new)));
    }

    static int execute(
            String[] args,
            Callable<Integer> collector,
            Callable<Integer> orchestrator,
            ToIntFunction<String[]> applier,
            ToIntFunction<String[]> chatEvaluator
    ) throws Exception {
        if (args.length == 0) {
            System.err.println("Expected a command: login, collect-batch, process-batch, "
                    + "process-urls, check-recommendations, resume, job-facts, finish-analysis, apply, chat-eval");
            return 2;
        }
        return switch (args[0]) {
            case "login", "collect-batch" -> collector.call();
            case "process-batch", "process-urls", "check-recommendations", "resume", "job-facts", "finish-analysis" ->
                    orchestrator.call();
            case "apply" -> applier.applyAsInt(Arrays.copyOfRange(args, 1, args.length));
            case "chat-eval" -> chatEvaluator.applyAsInt(Arrays.copyOfRange(args, 1, args.length));
            default -> {
                System.err.println("Unknown command: " + args[0]);
                yield 2;
            }
        };
    }
}
