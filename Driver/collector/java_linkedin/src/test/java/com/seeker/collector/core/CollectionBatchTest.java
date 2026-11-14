package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.PythonGateway;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CollectionBatchTest {
    @Test void sourceSettingsKeepOrderAndReloadFileChanges(@TempDir Path root) throws Exception {
        Files.createDirectories(root.resolve("config"));
        Path config = root.resolve("config/collector.properties");
        Files.writeString(config, "sources=linkedin,justjoin\n");
        assertEquals(List.of("linkedin", "justjoin"), CollectorSettings.loadSources(root));
        Files.writeString(config, "sources=justjoin\n");
        assertEquals(List.of("justjoin"), CollectorSettings.loadSources(root));
        for (String invalid : List.of("", "other", "linkedin,linkedin"))
            assertThrows(IllegalArgumentException.class, () -> CollectorSettings.sources(invalid));
    }

    @Test void sequentialSourcesHaveIndependentLimitsAndOneCombinedResult() throws Exception {
        var calls = new ArrayList<String>();
        var first = new FakeSource("linkedin", 2, calls);
        var second = new FakeSource("justjoin", 1, calls);
        var entries = List.of(first.entry(), second.entry());
        var updates = new ArrayList<ProgressSnapshot>();
        var finished = new ArrayList<List<Object>>();
        var report = run(entries, updates, finished);
        assertEquals(List.of("linkedin", "justjoin"), calls);
        assertEquals("mixed", report.source());
        assertEquals(3, report.acceptedCount());
        assertEquals(List.of("linkedin:1", "linkedin:2", "justjoin:1"),
                report.scope().stream().map(s -> s.source() + ":" + s.jobId()).toList());
        assertEquals(Map.of("raw_saved", 3), report.outcomes());
        assertEquals(List.of(2, 1), report.sourceResults().stream().map(SourceCollectionResult::acceptedCount).toList());
        assertEquals(List.of(2, 1), report.sourceResults().stream().map(SourceCollectionResult::limit).toList());
        assertEquals(Map.of("languages", List.of(Map.of("code", "en", "level", "B2"))), report.scope().getLast().fields());
        assertEquals(1, finished.size());
        assertEquals(List.of("run", "complete", "sources_complete", 3, ""), finished.getFirst());
        assertTrue(updates.stream().allMatch(update -> update.runId().equals("run")));
        assertEquals("3", updates.getLast().fields().get("Accepted total"));
        assertEquals("2 / 2", updates.getLast().fields().get("linkedin accepted / limit"));
        assertEquals("1 / 1", updates.getLast().fields().get("justjoin accepted / limit"));
        assertEquals("native", updates.getFirst().fields().get("Search"));
    }

    @Test void eachSourceAloneUsesTheSameRunAndPreservesItsProgressShape() throws Exception {
        for (String name : List.of("linkedin", "justjoin")) {
            var updates = new ArrayList<ProgressSnapshot>();
            var finished = new ArrayList<List<Object>>();
            var report = run(List.of(new FakeSource(name, 1, new ArrayList<>()).entry()), updates, finished);
            assertEquals(name, report.source());
            assertEquals(1, report.acceptedCount());
            assertEquals(1, report.sourceResults().size());
            assertFalse(updates.getLast().fields().containsKey("Accepted total"));
            assertEquals("limit_reached", finished.getFirst().get(2));
        }
    }

    @Test void stoppedSourceDoesNotStartTheNextOne() throws Exception {
        var calls = new ArrayList<String>();
        var first = new FakeSource("linkedin", 1, calls);
        first.status = "blocked";
        var report = run(List.of(first.entry(), new FakeSource("justjoin", 1, calls).entry()),
                new ArrayList<>(), new ArrayList<>());
        assertEquals("blocked", report.status());
        assertEquals(List.of("linkedin"), calls);
        assertEquals(1, report.sourceResults().size());
    }

    @Test void exceptionPropagatesWithoutPartialFailureContinuation() {
        var calls = new ArrayList<String>();
        var first = new FakeSource("linkedin", 1, calls);
        first.fail = true;
        var finished = new ArrayList<List<Object>>();
        assertThrows(IOException.class, () -> run(List.of(first.entry(), new FakeSource("justjoin", 1, calls).entry()),
                new ArrayList<>(), finished));
        assertEquals(List.of("linkedin"), calls);
        assertEquals(List.of("run", "blocked", "java_exception", 0, "HTTP failed"), finished.getFirst());
    }

    @Test void configuredJustJoinDoesNotLoadLinkedInPreferencesOrStartItsBrowser(@TempDir Path root) throws Exception {
        Path collector = root.resolve("Driver/collector");
        Files.createDirectories(collector.resolve("config"));
        Files.createDirectories(collector.resolve("java_linkedin"));
        Files.createDirectories(root.resolve("Tools"));
        Files.createDirectories(root.resolve("Data/Profile/Driver/collector"));
        Files.writeString(collector.resolve("config/collector.properties"), "sources=justjoin\n");
        Files.writeString(collector.resolve("config/linkedin.properties"), "");
        Files.writeString(collector.resolve("config/justjoin.properties"), "itemsCount=1\ndelaySeconds=0\n");
        Files.writeString(root.resolve("Data/Profile/Driver/collector/justjoin.properties"), "mainTech=java\nlimit=5\n");
        Files.writeString(collector.resolve("java_linkedin/runtime.properties"), "raw.directory=raw\nprofile.directory=unused-profile\n");
        Files.writeString(root.resolve("Tools/runtime.properties"), """
                database.path=jobs.sqlite
                python.library=unused-python.dll
                jpy.library=unused-jpy.dll
                jdl.library=unused-jdl.dll
                python.home=unused-python
                python.executable=unused-python.exe
                """);
        var api = new CollectorApi(root);
        var stop = new RuntimeException("Stop before Python initialization or network");
        assertSame(stop, assertThrows(RuntimeException.class, () -> api.collectBatch(update -> {
            assertEquals("justjoin", update.fields().get("Source"));
            assertEquals("5", update.fields().get("Limit"));
            assertTrue(update.runId().endsWith("-batch-justjoin"));
            throw stop;
        })));
    }

    @Test
    @EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
    void mixedCollectionUsesOneNativeRunAndRegistersBothSources(@TempDir Path temporary) throws Exception {
        Path root = Path.of(System.getProperty("seeker.project.root"));
        var paths = new ProjectPaths(root.resolve("Driver"), root.resolve("Driver/collector"),
                root.resolve("Driver/collector/config/linkedin.properties"), temporary.resolve("jobs.sqlite"),
                temporary.resolve("raw"), temporary.resolve("unused-profile"));
        var runtime = com.seeker.python.PythonRuntime.load(root.resolve("Tools/runtime.properties"));
        var limits = new LinkedHashMap<String, Integer>();
        var sinks = new LinkedHashMap<String, CollectionAccumulator>();
        var entries = new ArrayList<CollectionService.SourceRun>();
        for (String name : List.of("linkedin", "justjoin")) {
            var source = new FakeSource(name, 1, new ArrayList<>());
            source.persist = true;
            var entry = source.entry();
            limits.put(name, 1); sinks.put(name, entry.sink()); entries.add(entry);
        }
        try (var python = new com.seeker.collector.common.python.JpyPythonGateway(paths, runtime)) {
            python.startRun("run", Map.of("sources", List.of("linkedin", "justjoin")));
            var report = CollectionService.runBatch(entries, new VacancyProcessor(python), python, "run",
                    new BatchProgress(ignored -> {}, limits, sinks));
            assertEquals(2, report.acceptedCount());
            assertEquals(2, report.sourceResults().size());
            assertEquals("B2", ((Map<?, ?>)((List<?>)report.scope().getLast().fields().get("languages")).getFirst()).get("level"));
            for (var entry : entries) assertEquals("duplicate_source_job_id", python.decidePreview(
                    ((FakeSource)entry.source()).preview("1")).rule());
            try (var sqlite = org.jpy.PyModule.importModule("sqlite3");
                 var connection = sqlite.callMethod("connect", paths.databasePath().toString())) {
                try (var cursor = connection.callMethod("execute", "SELECT run_id,accepted_count,collection_status FROM collection_runs");
                     var rows = cursor.callMethod("fetchall")) {
                    assertEquals("[('run', 2, 'complete')]", rows.repr());
                }
                try (var cursor = connection.callMethod("execute", "SELECT source,source_job_id,collection_run_id FROM source_jobs ORDER BY source");
                     var rows = cursor.callMethod("fetchall")) {
                    assertEquals("[('justjoin', '1', 'run'), ('linkedin', '1', 'run')]", rows.repr());
                } finally { try (var ignored = connection.callMethod("close")) { } }
            }
        }
    }

    private CollectionReport run(List<CollectionService.SourceRun> entries, List<ProgressSnapshot> updates,
                                 List<List<Object>> finished) throws Exception {
        var limits = new LinkedHashMap<String, Integer>();
        var sinks = new LinkedHashMap<String, CollectionAccumulator>();
        entries.forEach(e -> { limits.put(e.source().source(), e.limit()); sinks.put(e.source().source(), e.sink()); });
        var python = (PythonGateway)Proxy.newProxyInstance(PythonGateway.class.getClassLoader(), new Class<?>[]{PythonGateway.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("finishRun")) { finished.add(Arrays.asList(args)); return null; }
                    throw new AssertionError(method.getName());
                });
        return CollectionService.runBatch(entries, new VacancyProcessor(python), python, "run", new BatchProgress(updates::add, limits, sinks));
    }

    private static final class FakeSource implements CollectorSource {
        final String name;
        final int limit;
        final List<String> calls;
        String status = "complete";
        boolean fail;
        boolean persist;
        FakeSource(String name, int limit, List<String> calls) { this.name = name; this.limit = limit; this.calls = calls; }
        CollectionService.SourceRun entry() { return new CollectionService.SourceRun(this, limit, new CollectionAccumulator()); }
        public String source() { return name; }
        public SourceResult collectBatch(VacancyProcessor processor, CollectionSink sink) throws IOException {
            calls.add(name);
            if (fail) throw new IOException("HTTP failed");
            for (int i = 1; i <= 3 && sink.acceptedCount() < limit; i++) {
                var fields = Map.<String, Object>of("languages", List.of(Map.of("code", "en", "level", "B2")));
                if (persist) {
                    var preview = preview(Integer.toString(i));
                    assertTrue(processor.decidePreview(preview).shouldOpen());
                    processor.saveRaw(preview, name.equals("linkedin") ? "<h2>About the job</h2><p>Build Java backend services.</p>"
                            : "{\"body\":\"Build Java backend services.\"}", name.equals("linkedin") ? "html" : "json", "script");
                    sink.record(processor.process(new PreparedVacancy(preview, "Build Java backend services.", null, fields)));
                } else sink.record(new ProcessResult(name, Integer.toString(i), "Java", "raw_saved", "", fields));
            }
            return new SourceResult(status, "limit_reached", "");
        }
        Preview preview(String id) {
            return new Preview(name, id, name.equals("linkedin") ? "https://www.linkedin.com/jobs/view/" + id + "/"
                    : "https://justjoin.it/job-offer/" + id, "Java Developer", "Collection test fixture", "", "remote", "", "test", 0, 1);
        }
        public SourceResult collectUrls(List<String> urls, VacancyProcessor processor, CollectionSink sink) { throw new AssertionError(); }
        public ProgressSnapshot startingSnapshot(String action, Integer count) { return snapshot(action, ""); }
        public ProgressSnapshot snapshot(String status, String message) {
            return new ImmutableProgressSnapshot("run", "Collection", status, "Collection", Map.of("Search", "native"), message);
        }
    }
}
