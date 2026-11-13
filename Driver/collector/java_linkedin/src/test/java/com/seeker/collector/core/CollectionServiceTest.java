package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.PythonGateway;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionServiceTest {
    @Test void coreFormsReportAndFinishesRunWithTheSameFinalProgress() throws Exception {
        List<List<Object>> finished = new ArrayList<>();
        PythonGateway python = gateway(finished);
        CollectionAccumulator sink = new CollectionAccumulator();
        CollectorSource source = source(sink, false);
        List<ProgressSnapshot> progress = new ArrayList<>();
        CollectionReport report = CollectionService.run(source, new VacancyProcessor(python), python,
                sink, "run", null, progress::add);

        assertEquals("complete", report.status());
        assertEquals(List.of("1", "2"), report.scope().stream().map(ScopeItem::jobId).toList());
        assertEquals(Map.of("raw_saved", 2, "content_filtered", 1), report.outcomes());
        assertEquals(1, progress.size());
        assertEquals("Collection", progress.getFirst().stage());
        assertEquals("Collection 2", progress.getFirst().progress());
        assertEquals(List.of(List.of("run", "complete", "plan_exhausted", 2, "")), finished);
        var json = JsonSupport.GSON.toJsonTree(report).getAsJsonObject();
        assertEquals("run", json.get("run_id").getAsString());
        assertEquals(2, json.get("accepted_count").getAsInt());
        assertEquals("1", json.getAsJsonArray("scope").get(0).getAsJsonObject().get("job_id").getAsString());
        assertFalse(json.getAsJsonArray("scope").get(0).getAsJsonObject().has("fields"));
    }

    @Test void failuresPropagateAndFinishTheRunThroughExistingErrorHandling() {
        List<List<Object>> finished = new ArrayList<>();
        PythonGateway python = gateway(finished);
        CollectionAccumulator sink = new CollectionAccumulator();
        IOException error = assertThrows(IOException.class, () -> CollectionService.run(source(sink, true),
                new VacancyProcessor(python), python, sink, "run", List.of("url"), ignored -> {}));
        assertEquals("source failed", error.getMessage());
        assertEquals(List.of(List.of("run", "blocked", "java_exception", 0, "source failed")), finished);
    }

    private static PythonGateway gateway(List<List<Object>> finished) {
        return (PythonGateway) Proxy.newProxyInstance(PythonGateway.class.getClassLoader(),
                new Class<?>[]{PythonGateway.class}, (proxy, method, args) -> {
                    if (method.getName().equals("finishRun")) { finished.add(Arrays.asList(args)); return null; }
                    throw new AssertionError("Unexpected operation: " + method.getName());
                });
    }

    private static CollectorSource source(CollectionSink sink, boolean fail) {
        return new CollectorSource() {
            public String source() { return "linkedin"; }
            public SourceResult collectBatch(VacancyProcessor processor, CollectionSink output) throws IOException {
                if (fail) throw new IOException("source failed");
                output.record(new ProcessResult("linkedin", "1", "Java", "raw_saved", ""));
                output.record(new ProcessResult("linkedin", "9", "Java", "content_filtered", "rejected"));
                output.record(new ProcessResult("linkedin", "2", "Java", "raw_saved", ""));
                return new SourceResult("complete", "plan_exhausted", "");
            }
            public SourceResult collectUrls(List<String> urls, VacancyProcessor processor, CollectionSink output) throws IOException {
                assertEquals(List.of("url"), urls);
                return collectBatch(processor, output);
            }
            public ProgressSnapshot startingSnapshot(String action, Integer count) { return snapshot(action, ""); }
            public ProgressSnapshot snapshot(String status, String message) {
                return new ImmutableProgressSnapshot("run", "Collection", status,
                        "Collection " + sink.acceptedCount(), Map.of(), message);
            }
        };
    }
}
