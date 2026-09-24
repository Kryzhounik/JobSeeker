package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.support.ProcessLog;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;

public final class Main {
    private static final Gson GSON = new Gson();

    private Main() {
    }

    public static void main(String[] args) {
        PrintStream consoleErr = System.err;
        int exitCode = 0;
        try (ProcessLog log = ProcessLog.install(Path.of("."))) {
            System.err.println("LinkedIn collector log: " + log.path());
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
        if (args.length != 2 || !"batch".equals(args[0])) {
            throw new IllegalArgumentException("Expected: batch <source>");
        }

        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(Path.of("."));
        CollectionReport report = orchestrator.batch(args[1]);
        var result = GSON.toJsonTree(report).getAsJsonObject();
        result.remove("pages");
        System.out.println(GSON.toJson(result));
    }
}
