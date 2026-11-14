package com.seeker.collector.justjoin;

import com.google.gson.JsonObject;
import com.seeker.collector.common.*;
import com.seeker.collector.common.python.PythonGateway;
import com.seeker.collector.core.CollectionAccumulator;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JustJoinSourceTest {
    @TempDir Path temporaryDirectory;
    HttpServer server;
    JsonObject offer;
    List<String> requests;
    List<PreparedVacancy> processed;
    List<String> saved;
    Map<Integer, JsonObject> listingPages;
    JustJoinConfig config;

    @BeforeEach void setup() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/justjoin/offer.json"), StandardCharsets.UTF_8)) {
            offer = JsonSupport.GSON.fromJson(reader, JsonObject.class);
        }
        requests = new ArrayList<>(); processed = new ArrayList<>(); saved = new ArrayList<>();
        listingPages = new HashMap<>();
        config = new JustJoinConfig("java", "all-locations", "14", List.of("en", "uk"),
                List.of("mid", "senior"), "DESC", "published", 1, 1, 0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/offers", exchange -> {
            requests.add(exchange.getRequestURI().toString());
            String path = exchange.getRequestURI().getPath();
            int offset = path.equals("/offers") ? Integer.parseInt(Arrays.stream(exchange.getRequestURI().getQuery().split("&"))
                    .filter(part -> part.startsWith("from=")).findFirst().orElseThrow().substring(5)) : 0;
            String response = path.equals("/offers/count") ? "{\"count\":3}" :
                    path.equals("/offers") ? "{\"data\":[" + listingPages.getOrDefault(offset, listing()) + "]}" : offer.toString();
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); }

    JsonObject listing() { var copy = offer.deepCopy(); copy.remove("body"); return copy; }

    JustJoinSource source() {
        return new JustJoinSource(config, new JustJoinHttpClient(config,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/")), "test-run", ignored -> {});
    }

    VacancyProcessor processor(boolean open, boolean accept) {
        var gateway = (PythonGateway)Proxy.newProxyInstance(PythonGateway.class.getClassLoader(),
                new Class<?>[]{PythonGateway.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "decidePreview" -> new PreviewDecision(open ? "open" : "skip", open ? "" : "duplicate source_job_id",
                            open ? "" : "duplicate_source_job_id", List.of());
                    case "logOutcome" -> null;
                    case "saveRaw" -> {
                        assertEquals("json", args[2]); assertEquals("script", args[3]);
                        saved.add((String)args[1]); yield "unused.json";
                    }
                    case "processVacancy" -> {
                        var vacancy = (PreparedVacancy)args[0]; processed.add(vacancy);
                        yield new ProcessResult("justjoin", vacancy.preview().jobId(), vacancy.preview().title(),
                                accept ? "raw_saved" : "content_filtered", "", vacancy.fields());
                    }
                    default -> throw new AssertionError(method.getName());
                });
        return new VacancyProcessor(gateway);
    }

    @Test void httpOfferUsesSharedProcessingAndRetainsNativeFieldsAndReadableBody() throws Exception {
        var sink = new CollectionAccumulator();
        var source = source();
        var result = source.collectBatch(processor(true, true), sink);
        assertEquals("limit_reached", result.stopReason());
        assertEquals(1, processed.size());
        var vacancy = processed.getFirst();
        assertEquals(offer.get("slug").getAsString(), vacancy.preview().jobId());
        assertEquals("justjoin", vacancy.preview().source());
        assertFalse(vacancy.description().contains("<p>"));
        assertTrue(vacancy.description().contains("\nResponsibilities\n"));
        assertTrue(vacancy.description().contains("Design and develop end-to-end software solutions"));
        assertEquals("B2", ((Map<?, ?>)((List<?>)vacancy.fields().get("languages")).getFirst()).get("level"));
        assertEquals(5, ((Number)((Map<?, ?>)((List<?>)vacancy.fields().get("requiredSkills")).getFirst()).get("level")).intValue());
        assertTrue(vacancy.fields().containsKey("employmentTypes"));
        assertEquals(offer.get("body"), JsonSupport.GSON.fromJson(saved.getFirst(), JsonObject.class).get("body"));
        assertEquals(vacancy.fields(), sink.report(source.source(), "test-run", result, source.pages()).scope().getFirst().fields());
        assertEquals(3, requests.size(), "No next page after reaching accepted limit");
        assertTrue(requests.getFirst().contains("languages=en&languages=uk"));
        assertTrue(requests.getFirst().contains("experienceLevels=mid&experienceLevels=senior"));
    }

    @Test void previewRejectionSkipsDetailAndRepeatedSlugsAcrossPages() throws Exception {
        var sink = new CollectionAccumulator();
        var result = source().collectBatch(processor(false, true), sink);
        assertEquals("plan_exhausted", result.stopReason());
        assertEquals(Map.of("preview_filtered", 1), sink.outcomes());
        assertEquals(4, requests.size()); // count + three listing pages, no detail
        assertTrue(saved.isEmpty()); assertTrue(processed.isEmpty());
        assertTrue(requests.getLast().contains("from=2"));
    }

    @Test void contentRejectionsDoNotConsumeAcceptedLimit() throws Exception {
        var sink = new CollectionAccumulator();
        var result = source().collectBatch(processor(true, false), sink);
        assertEquals("plan_exhausted", result.stopReason());
        assertEquals(0, sink.acceptedCount());
        assertEquals(Map.of("content_filtered", 1), sink.outcomes());
        assertEquals(5, requests.size());
    }

    @Test void unlimitedCollectionVisitsDistinctPublicationsAcrossPages() throws Exception {
        config = new JustJoinConfig("java", "all-locations", "14", List.of("en"), List.of("senior"),
                "DESC", "published", 1, 0, 0);
        var second = listing();
        second.addProperty("slug", "second-publication");
        listingPages.put(1, second);
        var sink = new CollectionAccumulator();
        var result = source().collectBatch(processor(true, true), sink);
        assertEquals("plan_exhausted", result.stopReason());
        assertEquals(List.of(offer.get("slug").getAsString(), "second-publication"),
                processed.stream().map(vacancy -> vacancy.preview().jobId()).toList());
        assertEquals(2, sink.acceptedCount());
    }

    @Test void directUrlLoadsFullOfferAndUsesTheSamePreparation() throws Exception {
        var sink = new CollectionAccumulator();
        source().collectUrls(List.of(offer.get("url").getAsString()), processor(true, true), sink);
        assertEquals(1, requests.size()); assertEquals(1, sink.acceptedCount());
    }

    @Test void httpFailurePropagatesWithoutSavingOrPretendingItIsDuplicate() throws Exception {
        server.removeContext("/offers");
        assertThrows(IOException.class, () -> source().collectBatch(processor(true, true), new CollectionAccumulator()));
        assertTrue(processed.isEmpty()); assertTrue(saved.isEmpty());
    }

    @Test void htmlEntitiesAndLineBreaksArePreservedAsText() {
        offer.addProperty("body", "<p>Java &amp; SQL<br>Second line</p><ul><li>One</li><li>Two</li></ul><script>bad()</script>");
        assertEquals("Java & SQL\nSecond line\nOne\nTwo", source().prepare(offer, "slug").description());
    }

    @Test
    @EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
    void httpRetrievalPassesThroughRealSharedPythonStorageAndDeduplication() throws Exception {
        Path root = Path.of(System.getProperty("seeker.project.root"));
        var paths = new ProjectPaths(root.resolve("Driver"), root.resolve("Driver/collector"),
                root.resolve("Driver/collector/config/linkedin.properties"), temporaryDirectory.resolve("jobs.sqlite"),
                temporaryDirectory.resolve("raw"), temporaryDirectory.resolve("unused-profile"));
        var runtime = com.seeker.python.PythonRuntime.load(root.resolve("Tools/runtime.properties"));
        try (var python = new com.seeker.collector.common.python.JpyPythonGateway(paths, runtime)) {
            python.startRun("justjoin-http-test", config);
            var processor = new VacancyProcessor(python);
            var sink = new CollectionAccumulator();
            var source = source();
            var result = source.collectBatch(processor, sink);
            var report = sink.report(source.source(), "justjoin-http-test", result, source.pages());
            python.finishRun(report.runId(), result.status(), result.stopReason(), report.acceptedCount(), report.message());
            assertEquals(1, report.acceptedCount());
            assertEquals("B2", ((Map<?, ?>)((List<?>)report.scope().getFirst().fields().get("languages")).getFirst()).get("level"));
            assertTrue(Files.exists(paths.rawDirectory().resolve("pages/" + offer.get("slug").getAsString() + ".json")));
            assertEquals("duplicate_source_job_id", processor.decidePreview(source.prepare(offer,
                    offer.get("slug").getAsString()).preview()).rule());

            try (var sqlite = org.jpy.PyModule.importModule("sqlite3");
                 var texts = org.jpy.PyModule.importModule("db.readable_text");
                 var connection = sqlite.callMethod("connect", paths.databasePath().toString())) {
                try (var text = texts.callMethod("load_readable_text", connection, "justjoin", offer.get("slug").getAsString())) {
                    assertEquals(source.prepare(offer, offer.get("slug").getAsString()).description(), text.getStringValue());
                } finally {
                    try (var ignored = connection.callMethod("close")) { }
                }
            }
            requests.clear();
            var repeated = new CollectionAccumulator();
            source().collectBatch(processor, repeated);
            assertEquals(0, repeated.acceptedCount());
            assertEquals(Map.of("preview_filtered", 1), repeated.outcomes());
            assertTrue(requests.stream().noneMatch(path -> path.startsWith("/offers/" + offer.get("slug").getAsString())));
        }
    }
}
