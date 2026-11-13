package com.seeker.guifx;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.common.CollectionReport;
import com.seeker.collector.common.JsonSupport;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class CollectorProgressTest {
    @Test
    void progressContracts() throws Exception {
        ProcessCollectorClient runner = new ProcessCollectorClient(Path.of("."));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Z arbitrary field", "7 / 100");
        fields.put("A another field", "Anything");
        ProgressSnapshot direct = new CollectionProgress("test-run", "A backend stage",
                "A backend action", "7 / 100", fields, "123 Software Engineer\nChecking content");
        ProgressSnapshot progress = runner.parseProgress("COLLECTOR_PROGRESS "
                + JsonSupport.GSON.toJson(direct));
        if (!progress.runId().equals(direct.runId()) || !progress.stage().equals(direct.stage())
                || !progress.action().equals(direct.action()) || !progress.fields().equals(direct.fields())
                || !progress.progress().equals(direct.progress())
                || !progress.details().equals(direct.details())
                || !List.copyOf(progress.fields().keySet()).equals(List.copyOf(fields.keySet()))) {
            fail("Direct and process progress contracts differ");
        }
        fields.put("Late mutation", "not published");
        if (progress.fields().containsKey("Late mutation") || direct.fields().containsKey("Late mutation")) {
            fail("Snapshot fields are mutable");
        }
        assertThrows(UnsupportedOperationException.class, () -> progress.fields().put("Forbidden", "mutation"));
        ProgressSnapshot early = runner.parseProgress("""
                COLLECTOR_PROGRESS {"runId":null,"stage":"Stage","action":"Preparing","progress":"",
                "fields":{},"details":""}
                """);
        if (early.runId() != null || !early.fields().isEmpty()) {
            fail("Early snapshot without an assigned run ID was changed");
        }
        for (String invalid : new String[]{"COLLECTOR_PROGRESS {}", "invalid prefix",
                "COLLECTOR_PROGRESS {\"stage\":\"Stage\",\"action\":\"Action\",\"details\":\"\"}",
                "COLLECTOR_PROGRESS {\"stage\":\"Stage\",\"action\":\"Action\",\"fields\":{},\"details\":\"\"}"}) {
            assertThrows(IOException.class, () -> runner.parseProgress(invalid), invalid);
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
            fail("Stopped collection was not preserved");
        }
        if (!stopped.pages().isEmpty()) {
            fail("Optional fields were not normalized");
        }
        for (String invalid : new String[]{"", "{}", "{\"status\":\"complete\"}"}) {
            assertThrows(IOException.class, () -> runner.parseResult(invalid), invalid);
        }
    }

    @Test
    void modeSelection() throws IOException {
        Path root = Files.createTempDirectory("collector-mode-");
        Path config = root.resolve("GUI/JavaFX/backend.properties");
        CollectorClient direct = progress -> fail("Collection must not run in mode selection");
        try {
            if (CollectorClient.configured(root, direct) != direct) {
                fail("Default collector mode must be direct");
            }
            Files.createDirectories(config.getParent());
            Files.writeString(config, "collector.mode=process\n");
            if (!(CollectorClient.configured(root, direct) instanceof ProcessCollectorClient)) {
                fail("Process adapter was not selected");
            }
            Files.writeString(config, "collector.mode=direct\n");
            if (CollectorClient.configured(root, direct) != direct) {
                fail("Direct adapter was not selected");
            }
            Files.writeString(config, "collector.mode=unknown\n");
            assertThrows(IOException.class, () -> CollectorClient.configured(root, direct));
        } finally {
            Files.deleteIfExists(config);
            Files.deleteIfExists(config.getParent());
            Files.deleteIfExists(root.resolve("GUI"));
            Files.deleteIfExists(root);
        }
    }
}
