package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.PythonGateway;
import com.seeker.mailer.Mailer;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CollectionRecommendationsBatchTest {
    private static final String RUN_ID = "recommendation-run";

    @Test void disabledRecommendationsDoNotLoadAndSearchSourcesKeepConfiguredOrder() throws Exception {
        var fixture = fixture(List.of("justjoin", "linkedin"), 20, 20);
        var loaderCalls = new int[1];
        var report = run(fixture, settings(List.of("justjoin", "linkedin"), 20, 20, false), progress -> {
            loaderCalls[0]++;
            return List.of("recommendation-url");
        });

        assertEquals(0, loaderCalls[0]);
        assertEquals(List.of("justjoin:search", "linkedin:search"), fixture.calls);
        assertEquals(List.of("justjoin", "linkedin"), report.scope().stream().map(ScopeItem::source).distinct().toList());
        assertEquals(List.of(List.of(RUN_ID, "complete", "sources_complete", 40, "")), fixture.finishCalls);
    }

    @Test void unselectedLinkedInDoesNotLoadRecommendations() throws Exception {
        var fixture = fixture(List.of("justjoin"), 20, 20);
        var loaderCalls = new int[1];
        var report = run(fixture, settings(List.of("justjoin"), 20, 20, true), progress -> {
            loaderCalls[0]++;
            return List.of("recommendation-url");
        });

        assertEquals(0, loaderCalls[0]);
        assertEquals(List.of("justjoin:search"), fixture.calls);
        assertEquals(List.of("justjoin"), report.sourceResults().stream().map(SourceCollectionResult::source).toList());
    }

    @Test void emptyRecommendationUrlsContinueNormalSearchAndEmailProgressUsesSameRunId() throws Exception {
        var fixture = fixture(List.of("linkedin"), 20, 20);
        var progress = new ArrayList<ProgressSnapshot>();
        var recommendations = (CollectionService.Recommendations) listener -> {
            listener.accept(new Mailer.RecommendationStats(3, 2, 0));
            return List.of();
        };
        var report = run(fixture, settings(List.of("linkedin"), 20, 20, true), recommendations, progress);

        assertEquals(List.of("linkedin:search"), fixture.calls);
        assertTrue(progress.stream().filter(s -> s.stage().equals("Email recommendations"))
                .allMatch(s -> s.runId().equals(RUN_ID)));
        assertEquals("0", progress.stream().filter(s -> s.stage().equals("Email recommendations"))
                .reduce((a, b) -> b).orElseThrow().fields().get("Unique vacancy URLs"));
        assertEquals("linkedin", report.sourceResults().getFirst().source());
    }

    @Test void recommendationsFillLinkedInLimitAndSearchFillsRemainder() throws Exception {
        var fixture = fixture(List.of("linkedin"), 20, 20);
        var report = run(fixture, settings(List.of("linkedin"), 20, 20, true), ignored -> urls(12));

        assertEquals(12, fixture.urlCount);
        assertEquals(1, fixture.batchCalls);
        assertEquals(20, report.acceptedCount());
        assertEquals(20, report.sourceResults().getFirst().acceptedCount());
        assertEquals(1, report.sourceResults().size());
        assertEquals(Map.of("raw_saved", 20), report.outcomes());
        assertEquals(20, fixture.sink("linkedin").acceptedCount());
    }

    @Test void recommendationResultsAboveLinkedInLimitAreKeptAndSearchIsSkipped() throws Exception {
        var fixture = fixture(List.of("linkedin"), 20, 20);
        var report = run(fixture, settings(List.of("linkedin"), 20, 20, true), ignored -> urls(26));

        assertEquals(0, fixture.batchCalls);
        assertEquals(26, report.acceptedCount());
        assertEquals(26, report.scope().size());
        assertEquals(26, report.sourceResults().getFirst().acceptedCount());
        assertEquals(List.of(List.of(RUN_ID, "complete", "limit_reached", 26, "")), fixture.finishCalls);
    }

    @Test void recommendationCollectionPrecedesConfiguredSearchOrderAndJustJoinHasIndependentLimit() throws Exception {
        var fixture = fixture(List.of("justjoin", "linkedin"), 2, 1);
        var report = run(fixture, settings(List.of("justjoin", "linkedin"), 2, 1, true), ignored -> urls(1));

        assertEquals(List.of("linkedin:urls", "justjoin:search", "linkedin:search"), fixture.calls);
        assertEquals(List.of("linkedin", "justjoin", "linkedin"), report.scope().stream().map(ScopeItem::source).toList());
        assertEquals(2, report.sourceResults().stream().filter(r -> r.source().equals("linkedin")).findFirst().orElseThrow().limit());
        assertEquals(1, report.sourceResults().stream().filter(r -> r.source().equals("justjoin")).findFirst().orElseThrow().limit());
        assertEquals(3, report.acceptedCount());
        assertEquals(Map.of("raw_saved", 3), report.outcomes());
        assertEquals(List.of("justjoin", "linkedin"), report.sourceResults().stream().map(SourceCollectionResult::source).toList());
    }

    @Test void blockedRecommendationCollectionSkipsSearchButKeepsAcceptedRecommendationsInReport() throws Exception {
        var fixture = fixture(List.of("linkedin"), 20, 20);
        fixture.urlResult = new SourceResult("blocked", "recommendations_blocked", "could not collect URL");
        var report = run(fixture, settings(List.of("linkedin"), 20, 20, true), ignored -> List.of("recommendation-url"));

        assertEquals(List.of("linkedin:urls"), fixture.calls);
        assertEquals(1, report.acceptedCount());
        assertEquals("blocked", report.status());
        assertEquals(List.of(List.of(RUN_ID, "blocked", "recommendations_blocked", 1, "could not collect URL")), fixture.finishCalls);
    }

    @Test void recommendationLoaderIOExceptionFinishesBlockedWithoutCallingSources() throws Exception {
        var fixture = fixture(List.of("justjoin", "linkedin"), 20, 20);
        var error = assertThrows(IOException.class, () -> run(fixture,
                settings(List.of("justjoin", "linkedin"), 20, 20, true), ignored -> { throw new IOException("mail read failed"); }));

        assertEquals("mail read failed", error.getMessage());
        assertTrue(fixture.calls.isEmpty());
        assertEquals(List.of(List.of(RUN_ID, "blocked", "java_exception", 0, "mail read failed")), fixture.finishCalls);
    }

    @Test void laterSourceFailureFinishesWithAcceptedRecommendationsAndPriorSearchResults() throws Exception {
        var fixture = fixture(List.of("linkedin", "justjoin"), 5, 20);
        fixture.failSource = "justjoin";
        assertThrows(IOException.class, () -> run(fixture,
                settings(List.of("linkedin", "justjoin"), 5, 20, true), ignored -> urls(3)));

        assertEquals(List.of("linkedin:urls", "linkedin:search", "justjoin:search"), fixture.calls);
        assertEquals(List.of(List.of(RUN_ID, "blocked", "java_exception", 5, "justjoin failed")), fixture.finishCalls);
    }

    private static CollectionReport run(Fixture fixture, CollectionSettings settings,
                                        CollectionService.Recommendations recommendations) throws IOException {
        return run(fixture, settings, recommendations, new ArrayList<>());
    }

    private static CollectionReport run(Fixture fixture, CollectionSettings settings,
                                        CollectionService.Recommendations recommendations,
                                        List<ProgressSnapshot> progressSnapshots) throws IOException {
        var limits = new LinkedHashMap<String, Integer>();
        var sinks = new LinkedHashMap<String, CollectionAccumulator>();
        settings.sources().forEach(name -> {
            limits.put(name, name.equals("linkedin") ? settings.linkedin().limit() : settings.justjoin().limit());
            sinks.put(name, fixture.sink(name));
        });
        var batchProgress = new BatchProgress(progressSnapshots::add, limits, sinks);
        var runs = settings.sources().stream().map(name -> new CollectionService.SourceRun(
                fixture.sources.get(name), limits.get(name), sinks.get(name))).toList();
        return CollectionService.runBatch(runs, new VacancyProcessor(fixture.python), fixture.python,
                RUN_ID, batchProgress, settings, recommendations);
    }

    private static CollectionSettings settings(List<String> sources, int linkedinLimit, int justjoinLimit,
                                               boolean includeRecommendations) {
        var linkedin = new CollectionSettings.LinkedIn(List.of("Java"), List.of("Poland"), List.of(), List.of(),
                List.of(), List.of(), "week", linkedinLimit, includeRecommendations);
        var justjoin = new CollectionSettings.JustJoin("java", "all-locations", "", List.of(), List.of(),
                "DESC", "published", justjoinLimit);
        return new CollectionSettings(sources, linkedin,
                sources.contains("justjoin") ? justjoin : null);
    }

    private static List<String> urls(int count) {
        return java.util.stream.IntStream.range(0, count).mapToObj(i -> "url-" + i).toList();
    }

    private static Fixture fixture(List<String> sourceNames, int linkedinLimit, int justjoinLimit) throws IOException {
        return new Fixture(sourceNames, linkedinLimit, justjoinLimit);
    }

    private static final class Fixture {
        final List<String> calls = new ArrayList<>();
        final List<List<Object>> finishCalls = new ArrayList<>();
        final Map<String, CollectionAccumulator> sinks = new LinkedHashMap<>();
        final Map<String, CollectorSource> sources = new LinkedHashMap<>();
        final PythonGateway python = mock(PythonGateway.class);
        SourceResult urlResult = new SourceResult("complete", "urls_complete", "");
        String failSource;
        int urlCount;
        int batchCalls;

        Fixture(List<String> names, int linkedinLimit, int justjoinLimit) throws IOException {
            doAnswer(call -> { finishCalls.add(Arrays.asList(call.getArguments())); return null; })
                    .when(python).finishRun(anyString(), anyString(), anyString(), anyInt(), anyString());
            for (String name : names) {
                sinks.put(name, new CollectionAccumulator());
                sources.put(name, createSource(name, name.equals("linkedin") ? linkedinLimit : justjoinLimit));
            }
        }

        private CollectorSource createSource(String name, int limit) throws IOException {
            var source = mock(CollectorSource.class);
            when(source.source()).thenReturn(name);
            when(source.pages()).thenReturn(List.of());
            when(source.startingSnapshot(anyString(), any())).thenAnswer(call -> snapshot(name, "starting"));
            when(source.snapshot(anyString(), anyString())).thenAnswer(call -> snapshot(name, call.getArgument(0)));
            when(source.collectUrls(anyList(), any(), any()))
                    .thenAnswer(call -> collectUrls(name, call.getArgument(0), call.getArgument(2)));
            when(source.collectBatch(any(), any()))
                    .thenAnswer(call -> collectSearch(name, limit, call.getArgument(1)));
            return source;
        }

        private SourceResult collectUrls(String name, List<String> input, CollectionSink sink) {
            assertSame(sink(name), sink);
            calls.add(name + ":urls");
            urlCount += input.size();
            input.forEach(url -> sink.record(accepted(name, "recommendation-" + url)));
            return urlResult;
        }

        private SourceResult collectSearch(String name, int limit, CollectionSink sink) throws IOException {
            assertSame(sink(name), sink);
            calls.add(name + ":search");
            batchCalls++;
            if (name.equals(failSource)) throw new IOException(name + " failed");
            int remaining = Math.max(0, limit - sink.acceptedCount());
            for (int i = 0; i < remaining; i++) sink.record(accepted(name, "search-" + i));
            return new SourceResult("complete", "plan_exhausted", "");
        }

        CollectionAccumulator sink(String name) { return Objects.requireNonNull(sinks.get(name)); }

        private static ProcessResult accepted(String source, String id) {
            return new ProcessResult(source, id, "Title " + id, "raw_saved", "");
        }

        private ProgressSnapshot snapshot(String source, String status) {
            return new ImmutableProgressSnapshot(RUN_ID, "Collection", status, "Collection",
                    Map.of("Accepted", Integer.toString(sink(source).acceptedCount())), "");
        }
    }
}
