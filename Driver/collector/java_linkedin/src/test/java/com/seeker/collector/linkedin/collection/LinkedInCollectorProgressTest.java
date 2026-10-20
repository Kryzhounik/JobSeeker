package com.seeker.collector.linkedin.collection;

import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.linkedin.python.PythonGateway;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkedInCollectorProgressTest {
    private static final CollectorConfig CONFIG = new CollectorConfig(
            "Java", List.of("Poland"), List.of(), List.of("remote"), List.of(),
            List.of(), "", "", 100, 0, "chrome", 90, 60, 3, 20);

    @Test
    void sendsFinalSnapshotToCallbackWithoutAConsoleProtocol() {
        List<CollectionProgress> updates = new ArrayList<>();
        PythonGateway python = gateway(false);
        CollectionReport result = new LinkedInCollector(null, python, CONFIG,
                "callback-run", updates::add).runFromUrls(List.of());

        assertEquals("complete", result.status());
        assertEquals(1, updates.size());
        assertEquals("callback-run", updates.getFirst().runId());
        assertEquals("complete", updates.getFirst().stage());
        assertEquals(100, updates.getFirst().limit());
        assertEquals(result.outcomes(), updates.getFirst().outcomes());
    }

    @Test
    void sendsBlockedStateToCallbackWhenCollectionFails() {
        List<CollectionProgress> updates = new ArrayList<>();
        CollectionReport result = new LinkedInCollector(null, gateway(true), CONFIG,
                "failed-run", updates::add).runBatch();

        assertEquals("blocked", result.status());
        assertEquals("failed-run", updates.getLast().runId());
        assertEquals("blocked", updates.getLast().stage());
        assertEquals("test failure", updates.getLast().message());
    }

    @Test
    void snapshotsDoNotChangeWhenCountersAreUpdated() {
        Map<String, Integer> counters = new HashMap<>(Map.of("raw_saved", 1));
        CollectionProgress progress = new CollectionProgress("run", "processing", "", "",
                1, 25, 1, 100, counters, "");
        counters.put("raw_saved", 2);
        assertEquals(1, progress.outcomes().get("raw_saved"));
        assertThrows(UnsupportedOperationException.class,
                () -> progress.outcomes().put("raw_saved", 3));
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
