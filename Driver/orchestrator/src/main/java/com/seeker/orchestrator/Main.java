package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.collector.linkedin.support.CliOutput;
import com.seeker.collector.linkedin.support.ProcessLog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

public final class Main {
    private static final Gson GSON = new Gson();

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream consoleErr = System.err;
        int exitCode = 0;
        try (ProcessLog log = ProcessLog.install(Path.of("."))) {
            System.err.println("Java workflow log: " + log.path());
            try {
                run(args);
            } catch (Throwable error) {
                error.printStackTrace(System.err);
                exitCode = 1;
            }
        } catch (IOException error) {
            error.printStackTrace(consoleErr);
            exitCode = 1;
        }
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static void run(String[] args) throws IOException {
        try (CliOutput ignored = CliOutput.redirectPython(Path.of("."))) {
            runWorkflow(args);
        }
    }

    private static void runWorkflow(String[] args) throws IOException {
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(Path.of("."), CliOutput::printProgress);
        if ((args.length >= 2 && "process-urls".equals(args[0]))
                || (args.length == 2 && (
                        "process-batch".equals(args[0]) || "resume".equals(args[0])
                ))) {
            WorkflowResult report = switch (args[0]) {
                case "process-batch" -> orchestrator.processBatch(args[1]);
                case "process-urls" -> orchestrator.processUrls(
                        Arrays.copyOfRange(args, 1, args.length)
                );
                default -> orchestrator.resume(args[1]);
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
                    ? orchestrator.jobFacts(args[1], args[2], scope)
                    : orchestrator.finishAnalysis(args[1], args[2], scope);
            System.out.println(GSON.toJson(result));
            return;
        }
        throw new IllegalArgumentException(
                "Expected: process-batch <source>, process-urls <url> [<url> ...], resume <run-id>, or "
                        + "job-facts|finish-analysis <run-id> <source> "
                        + "<comma-separated-job-ids>"
        );
    }
}
