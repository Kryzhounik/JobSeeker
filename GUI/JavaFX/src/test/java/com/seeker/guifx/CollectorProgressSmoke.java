package com.seeker.guifx;

import java.io.IOException;
import java.nio.file.Path;

public final class CollectorProgressSmoke {
    public static void main(String[] args) throws Exception {
        CollectorRunner runner = new CollectorRunner(Path.of("."));
        CollectorRunner.Progress starting = runner.parseProgress("""
                COLLECTOR_PROGRESS {"run_id":"test-run","stage":"starting_browser",
                "limit":100,"outcomes":{}}
                """);
        if (!starting.runId().equals("test-run") || starting.limit() != 100) {
            throw new AssertionError("Early run ID or limit was lost");
        }
        CollectorRunner.Progress progress = runner.parseProgress("""
                COLLECTOR_PROGRESS {"run_id":"test-run","stage":"filtering_content",
                "location":"Ukraine:priority","search":"priority","page":2,"cards":25,
                "accepted_count":7,"limit":100,
                "outcomes":{"raw_saved":7,"preview_filtered":12,"content_filtered":3,"open_failed":1},
                "message":"123 Software Engineer\\nChecking content"}
                """);
        if (progress.accepted() != 7 || progress.page() != 2 || progress.cards() != 25
                || !progress.location().equals("Ukraine:priority")
                || progress.outcomes().get("preview_filtered") != 12
                || !progress.message().contains("\nChecking content")) {
            throw new AssertionError("Progress fields were not parsed");
        }
        CollectorRunner.Result stopped = runner.parseResult("""
                {"run_id":"test-run","status":"blocked","accepted_count":7,
                "outcomes":{"raw_saved":7,"preview_filtered":12},"message":"Browser disconnected"}
                """);
        if (!stopped.status().equals("blocked") || !stopped.runId().equals("test-run")
                || stopped.accepted() != 7 || !stopped.message().equals("Browser disconnected")) {
            throw new AssertionError("Stopped collection was not preserved");
        }
        for (String invalid : new String[]{"", "{}", "{\"status\":\"complete\"}"}) {
            try {
                runner.parseResult(invalid);
                throw new AssertionError("Incomplete final result was accepted");
            } catch (IOException expected) {
                // A missing final report must leave the last progress snapshot visible.
            }
        }
        System.out.println("Collector progress parsing OK");
    }
}
