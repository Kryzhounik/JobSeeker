package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.io.IOException;
import java.nio.file.Path;

public final class Main {
    private static final Gson GSON = new Gson();

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2 || !"batch".equals(args[0])) {
            throw new IllegalArgumentException("Expected: batch <source>");
        }

        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(Path.of("."));
        CollectionReport report = orchestrator.batch(args[1]);
        System.out.println(GSON.toJson(report));
    }
}
