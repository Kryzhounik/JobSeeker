package com.seeker.guifx;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.support.JsonSupport;
import com.seeker.tools.api.ProgressSnapshot;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CollectorProgressSmoke {
    public static void main(String[] args) throws Exception {
        ProcessCollectorClient runner = new ProcessCollectorClient(Path.of("."));
        checkModeSelection();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Z arbitrary field", "7 / 100");
        fields.put("A another field", "Anything");
        ProgressSnapshot direct = new CollectionProgress("test-run", "A backend stage",
                "A backend action", fields, "123 Software Engineer\nChecking content");
        ProgressSnapshot progress = runner.parseProgress("COLLECTOR_PROGRESS "
                + JsonSupport.GSON.toJson(direct));
        if (!progress.runId().equals(direct.runId()) || !progress.stage().equals(direct.stage())
                || !progress.action().equals(direct.action()) || !progress.fields().equals(direct.fields())
                || !progress.details().equals(direct.details())
                || !List.copyOf(progress.fields().keySet()).equals(List.copyOf(fields.keySet()))) {
            throw new AssertionError("Direct and process progress contracts differ");
        }
        fields.put("Late mutation", "not published");
        if (progress.fields().containsKey("Late mutation") || direct.fields().containsKey("Late mutation")) {
            throw new AssertionError("Snapshot fields are mutable");
        }
        try {
            progress.fields().put("Forbidden", "mutation");
            throw new AssertionError("Parsed snapshot fields are writable");
        } catch (UnsupportedOperationException expected) {
            // Process snapshots have the same immutability as direct callbacks.
        }
        ProgressSnapshot early = runner.parseProgress("""
                COLLECTOR_PROGRESS {"runId":null,"stage":"Stage","action":"Preparing",
                "fields":{},"details":""}
                """);
        if (early.runId() != null || !early.fields().isEmpty()) {
            throw new AssertionError("Early snapshot without an assigned run ID was changed");
        }
        for (String invalid : new String[]{"COLLECTOR_PROGRESS {}", "invalid prefix",
                "COLLECTOR_PROGRESS {\"stage\":\"Stage\",\"action\":\"Action\",\"details\":\"\"}"}) {
            try {
                runner.parseProgress(invalid);
                throw new AssertionError("Incomplete progress contract was accepted");
            } catch (IOException expected) {
                // No synthetic counters or stages should be substituted.
            }
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
        if (!stopped.pages().isEmpty()) {
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
        CollectorClient direct = progress -> { throw new AssertionError("Collection must not run in mode selection"); };
        try {
            if (CollectorClient.configured(root, direct) != direct) {
                throw new AssertionError("Default collector mode must be direct");
            }
            Files.createDirectories(config.getParent());
            Files.writeString(config, "collector.mode=process\n");
            if (!(CollectorClient.configured(root, direct) instanceof ProcessCollectorClient)) {
                throw new AssertionError("Process adapter was not selected");
            }
            Files.writeString(config, "collector.mode=direct\n");
            if (CollectorClient.configured(root, direct) != direct) {
                throw new AssertionError("Direct adapter was not selected");
            }
            Files.writeString(config, "collector.mode=unknown\n");
            try {
                CollectorClient.configured(root, direct);
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
