package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.tools.api.ProgressSnapshot;
import com.google.gson.GsonBuilder;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.collector.linkedin.support.CliOutput;
import com.seeker.collector.linkedin.support.ProcessLog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public final class Main {
    static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = execute(args);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int execute(String[] args) {
        PrintStream consoleErr = System.err;
        int exitCode = 0;
        try (ProcessLog log = ProcessLog.install(Path.of("."))) {
            System.err.println("Java workflow log: " + log.path());
            try {
                run(args);
            } catch (UsageLimitExceededException limit) {
                System.err.println("Codex usage limit: " + limit.getMessage());
                if (limit.getRetryAt() != null) System.err.println("Retry at: " + limit.getRetryAt());
                exitCode = 3;
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                exitCode = 1;
            }
        } catch (IOException error) {
            error.printStackTrace(consoleErr);
            exitCode = 1;
        }
        return exitCode;
    }

    private static void run(String[] args) throws IOException, UsageLimitExceededException {
        try (CliOutput ignored = CliOutput.redirectPython(Path.of("."))) {
            runWorkflow(args);
        }
    }

    private static void runWorkflow(String[] args) throws IOException, UsageLimitExceededException {
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(Path.of("."));
        Consumer<ProgressSnapshot> progress = update ->
                System.err.println("WORKFLOW_PROGRESS " + GSON.toJson(update));
        if ((args.length == 1 && "check-recommendations".equals(args[0]))
                || (args.length >= 2 && "process-urls".equals(args[0]))
                || (args.length == 2 && (
                        "process-batch".equals(args[0]) || "resume".equals(args[0])
                ))) {
            WorkflowResult report = switch (args[0]) {
                case "check-recommendations" -> orchestrator.checkRecommendations(progress);
                case "process-batch" -> orchestrator.processBatch(args[1], progress);
                case "process-urls" -> orchestrator.processUrls(
                        Arrays.asList(Arrays.copyOfRange(args, 1, args.length)), progress
                );
                default -> orchestrator.resume(args[1], progress);
            };
            System.out.println(GSON.toJson(report));
            return;
        }
        if (args.length == 4 && (
                "job-facts".equals(args[0])
                        || "finish-analysis".equals(args[0])
        )) {
            List<ScopeItem> scope = Arrays.stream(args[3].split(","))
                    .map(String::strip)
                    .filter(jobId -> !jobId.isEmpty())
                    .map(jobId -> new ScopeItem(args[2], jobId, ""))
                    .toList();
            if (scope.isEmpty()) {
                throw new IllegalArgumentException(args[0] + " scope is empty");
            }
            StageResult result = "job-facts".equals(args[0])
                    ? orchestrator.jobFacts(args[1], args[2], scope, progress)
                    : orchestrator.finishAnalysis(args[1], args[2], scope, progress);
            System.out.println(GSON.toJson(result));
            return;
        }
        throw new IllegalArgumentException(
                "Expected: check-recommendations, process-batch <source>, process-urls <url> [<url> ...], resume <run-id>, or "
                        + "job-facts|finish-analysis <run-id> <source> "
                        + "<comma-separated-job-ids>"
        );
    }
}
