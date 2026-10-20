package com.seeker.guifx;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CollectorProgressSmoke {
    public static void main(String[] args) throws Exception {
        ProcessCollectorClient runner = new ProcessCollectorClient(Path.of("."));
        checkModeSelection();
        CollectionProgress starting = runner.parseProgress("""
                COLLECTOR_PROGRESS {"run_id":"test-run","stage":"starting_browser",
                "limit":100,"outcomes":{}}
                """);
        if (!starting.runId().equals("test-run") || starting.limit() != 100) {
            throw new AssertionError("Early run ID or limit was lost");
        }
        CollectionProgress progress = runner.parseProgress("""
                COLLECTOR_PROGRESS {"run_id":"test-run","stage":"filtering_content",
                "location":"Ukraine:priority","search":"priority","page":2,"cards":25,
                "accepted_count":7,"limit":100,
                "outcomes":{"raw_saved":7,"preview_filtered":12,"content_filtered":3,"open_failed":1},
                "message":"123 Software Engineer\\nChecking content"}
                """);
        if (progress.acceptedCount() != 7 || progress.page() != 2 || progress.cards() != 25
                || !progress.location().equals("Ukraine:priority")
                || progress.outcomes().get("preview_filtered") != 12
                || !progress.message().contains("\nChecking content")) {
            throw new AssertionError("Progress fields were not parsed");
        }
        CollectionReport stopped = runner.parseResult("""
                {"source":"linkedin","run_id":"test-run","status":"blocked","accepted_count":7,
                "scope":[{"source":"linkedin","job_id":"123","title":"Java Developer"}],
                "outcomes":{"raw_saved":7,"preview_filtered":12},
                "message":"Card did not materialize\\nURL: https://www.linkedin.com/jobs/view/123/"}
                """);
        if (!stopped.status().equals("blocked") || !stopped.runId().equals("test-run")
                || stopped.acceptedCount() != 7 || !stopped.scope().getFirst().jobId().equals("123")
                || !stopped.message().endsWith("\nURL: https://www.linkedin.com/jobs/view/123/")) {
            throw new AssertionError("Stopped collection was not preserved");
        }
        if (!starting.location().isEmpty() || !stopped.pages().isEmpty()) {
            throw new AssertionError("Optional fields were not normalized");
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

    private static void checkModeSelection() throws IOException {
        Path root = Files.createTempDirectory("collector-mode-");
        Path config = root.resolve("GUI/JavaFX/backend.properties");
        try {
            if (!(CollectorClient.configured(root) instanceof DirectCollectorClient)) {
                throw new AssertionError("Default collector mode must be direct");
            }
            Files.createDirectories(config.getParent());
            Files.writeString(config, "collector.mode=process\n");
            if (!(CollectorClient.configured(root) instanceof ProcessCollectorClient)) {
                throw new AssertionError("Process adapter was not selected");
            }
            Files.writeString(config, "collector.mode=direct\n");
            if (!(CollectorClient.configured(root) instanceof DirectCollectorClient)) {
                throw new AssertionError("Direct adapter was not selected");
            }
            Files.writeString(config, "collector.mode=unknown\n");
            try {
                CollectorClient.configured(root);
                throw new AssertionError("Unknown mode silently accepted");
            } catch (IOException expected) {
                // Configuration mistakes must not silently select another transport.
            }
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(config.getParent());
            Files.deleteIfExists(root.resolve("GUI"));
            Files.deleteIfExists(root);
        }
    }
}
