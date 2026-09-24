package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
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
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(Path.of("."));
        if (args.length == 2 && "batch".equals(args[0])) {
            CollectionReport report = orchestrator.batch(args[1]);
            var result = GSON.toJsonTree(report).getAsJsonObject();
            result.remove("pages");
            System.out.println(GSON.toJson(result));
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
            if ("job-facts".equals(args[0])) {
                orchestrator.jobFacts(args[1], args[2], scope);
            } else {
                orchestrator.finishAnalysis(args[1], args[2], scope);
            }
            var result = new com.google.gson.JsonObject();
            result.addProperty("source", args[2]);
            result.addProperty("run_id", args[1]);
            result.addProperty("status", "complete");
            result.addProperty("accepted_count", scope.size());
            result.add("scope", GSON.toJsonTree(scope));
            result.addProperty("message", "");
            System.out.println(GSON.toJson(result));
            return;
        }
        throw new IllegalArgumentException(
                "Expected: batch <source> or "
                        + "job-facts|finish-analysis <run-id> <source> "
                        + "<comma-separated-job-ids>"
        );
    }
}
