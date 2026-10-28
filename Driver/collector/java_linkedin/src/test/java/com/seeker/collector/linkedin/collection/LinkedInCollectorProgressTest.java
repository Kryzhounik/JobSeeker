package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.PythonGateway;
import com.seeker.collector.linkedin.support.CliOutput;
import com.seeker.collector.linkedin.support.JsonSupport;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkedInCollectorProgressTest {
    private static final CollectorConfig CONFIG = new CollectorConfig(
            "Java", List.of("Poland"), List.of(), List.of("remote"), List.of(),
            List.of(), "", "", 100, 0, "chrome", 90, 60, 3, 20);

    @Test
    void sendsFinalSnapshotToCallbackWithoutAConsoleProtocol() {
        List<ProgressSnapshot> updates = new ArrayList<>();
        PythonGateway python = gateway(false);
        CollectionReport result = new LinkedInCollector(null, python, CONFIG,
                "callback-run", updates::add).runFromUrls(List.of());

        assertEquals("complete", result.status());
        assertEquals(1, updates.size());
        assertEquals("callback-run", updates.getFirst().runId());
        assertFalse(updates.getFirst().fields().containsKey("Run ID"));
        assertEquals("Collection", updates.getFirst().stage());
        assertEquals("Collection complete", updates.getFirst().action());
        assertEquals("0 / 100", updates.getFirst().fields().get("Accepted / limit"));
    }

    @Test
    void sendsBlockedStateToCallbackWhenCollectionFails() {
        List<ProgressSnapshot> updates = new ArrayList<>();
        CollectionReport result = new LinkedInCollector(null, gateway(true), CONFIG,
                "failed-run", updates::add).runBatch();

        assertEquals("blocked", result.status());
        assertEquals("failed-run", updates.getLast().runId());
        assertEquals("Collection", updates.getLast().stage());
        assertEquals("Collection stopped", updates.getLast().action());
        assertEquals("test failure", updates.getLast().details());
    }

    @Test
    void processingFailureIsNotConvertedIntoIncompleteRaw() throws Exception {
        IllegalStateException failure = new IllegalStateException("content filter failed");
        PythonGateway python = (PythonGateway) Proxy.newProxyInstance(
                PythonGateway.class.getClassLoader(), new Class<?>[]{PythonGateway.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("processHtml")) throw failure;
                    throw new AssertionError("Unexpected gateway call: " + method.getName());
                });
        LinkedInCollector collector = new LinkedInCollector(null, python, CONFIG,
                "failed-filter-run", ignored -> { });
        var process = LinkedInCollector.class.getDeclaredMethod(
                "processHtml", Preview.class, LinkedInPageClient.JobDetails.class);
        process.setAccessible(true);
        Preview preview = new Preview("123", "https://www.linkedin.com/jobs/view/123/",
                "Java Developer", "Company", "", "", "", "Poland", 0, 1);

        InvocationTargetException error = assertThrows(InvocationTargetException.class,
                () -> process.invoke(collector, preview,
                        new LinkedInPageClient.JobDetails("html", "", "", "", false)));

        assertSame(failure, error.getCause());
    }

    @Test
    void snapshotsDoNotChangeWhenCountersAreUpdated() {
        Map<String, Integer> counters = new HashMap<>(Map.of("raw_saved", 1));
        CollectionProgress progress = new CollectionProgress("run", "processing", "", "",
                1, 25, 1, 100, counters, "");
        counters.put("raw_saved", 2);
        assertEquals("1", progress.fields().get("Processed"));
        assertThrows(UnsupportedOperationException.class,
                () -> progress.fields().put("Processed", "3"));
    }

    @Test
    void processOutputPreservesTheSameCompleteOrderedSnapshot() {
        ProgressSnapshot direct = new CollectionProgress("run", "opening_job", "Poland", "priority",
                2, 25, 7, 100, Map.of("raw_saved", 7, "age_filtered", 2), "Loading 123");
        PrintStream original = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream stream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setErr(stream);
            CliOutput.printProgress(direct);
        } finally {
            System.setErr(original);
        }
        String json = output.toString(StandardCharsets.UTF_8).strip().substring("COLLECTOR_PROGRESS ".length());
        ProgressSnapshot parsed = JsonSupport.GSON.fromJson(json, ImmutableProgressSnapshot.class);
        assertEquals(direct.runId(), parsed.runId());
        assertFalse(parsed.fields().containsKey("Run ID"));
        assertEquals("Collection", parsed.stage());
        assertEquals("Loading vacancy", parsed.action());
        assertEquals("Collection 7 / 100", direct.progress());
        assertEquals(direct.progress(), parsed.progress());
        assertEquals(direct.fields(), parsed.fields());
        assertEquals(new ArrayList<>(direct.fields().keySet()), new ArrayList<>(parsed.fields().keySet()));
        assertEquals(direct.details(), parsed.details());
    }

    private static PythonGateway gateway(boolean fail) {
        return (PythonGateway) Proxy.newProxyInstance(PythonGateway.class.getClassLoader(),
                new Class<?>[]{PythonGateway.class}, (proxy, method, args) -> {
                    if (method.getName().equals("priorityCompanyIds") && fail) {
                        throw new IllegalStateException("test failure");
                    }
                    if (method.getName().equals("finishRun")) return null;
                    throw new AssertionError("Unexpected gateway call: " + method.getName());
                });
    }
}
