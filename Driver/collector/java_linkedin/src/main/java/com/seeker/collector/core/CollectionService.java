package com.seeker.collector.core;

import com.seeker.logging.LogOutput;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.*;
import com.seeker.collector.linkedin.collection.LinkedInSource;
import com.seeker.collector.linkedin.config.*;
import com.seeker.collector.justjoin.JustJoinConfig;
import com.seeker.collector.justjoin.JustJoinSource;
import com.seeker.python.PythonRuntime;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.mailer.Mailer;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** Owns a collection run, common processing, lifecycle, and final report. */
public final class CollectionService {
    private final ProjectPaths paths;
    private final PythonRuntime pythonRuntime;

    public CollectionService(ProjectPaths paths, PythonRuntime pythonRuntime) {
        this.paths = paths;
        this.pythonRuntime = pythonRuntime;
    }

    public CollectionReport collectBatch(String runId, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectBatch(runId, (String)null, progress);
    }

    public CollectionReport collectBatch(String runId, String selectedSource, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectBatch(runId, CollectionSettingsLoader.load(paths, selectedSource), progress);
    }

    public CollectionReport collectBatch(String runId, CollectionSettings settings, Consumer<ProgressSnapshot> progress) throws IOException {
        Objects.requireNonNull(settings);
        List<String> names = settings.sources();
        runId += "-" + String.join("-", names);
        var configs = new LinkedHashMap<String, Object>();
        var limits = new LinkedHashMap<String, Integer>();
        var sinks = new LinkedHashMap<String, CollectionAccumulator>();
        for (String name : names) {
            if (name.equals("linkedin")) {
                var config = CollectorConfig.load(paths.configPath(), settings.linkedin());
                configs.put(name, config); limits.put(name, config.search().limit());
            } else {
                var config = JustJoinConfig.load(paths.driverRoot().getParent(), settings.justjoin());
                configs.put(name, config); limits.put(name, config.search().limit());
            }
            sinks.put(name, new CollectionAccumulator());
        }
        var batchProgress = new BatchProgress(progress, limits, sinks);
        var sources = new ArrayList<SourceRun>();
        for (String name : names) {
            Consumer<ProgressSnapshot> listener = update -> batchProgress.emit(name, update);
            CollectorSource source = name.equals("linkedin")
                    ? new LinkedInSource(paths, (CollectorConfig)configs.get(name), runId, listener)
                    : new JustJoinSource((JustJoinConfig)configs.get(name), runId, listener);
            sources.add(new SourceRun(source, limits.get(name), sinks.get(name)));
        }
        var first = sources.getFirst().source();
        batchProgress.emit(first.source(), first.startingSnapshot("starting", null));
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, Map.of("sources", names, "settings", configs));
            return runBatch(sources, new VacancyProcessor(python), python, runId, batchProgress, settings,
                    listener -> new Mailer(paths.driverRoot().getParent()).getRecommendationUrls(listener));
        }
    }

    record SourceRun(CollectorSource source, int limit, CollectionAccumulator sink) { }
    private record CollectedSource(CollectionReport report, String stopReason) { }

    @FunctionalInterface
    interface Recommendations {
        List<String> load(Consumer<Mailer.RecommendationStats> progress) throws IOException;
    }

    static CollectionReport runBatch(List<SourceRun> sources, VacancyProcessor processor, PythonGateway python,
                                     String runId, BatchProgress progress) throws IOException {
        return runBatch(sources, processor, python, runId, progress, null);
    }

    static CollectionReport runBatch(List<SourceRun> sources, VacancyProcessor processor, PythonGateway python,
                                     String runId, BatchProgress progress, CollectionSettings settings) throws IOException {
        return runBatch(sources, processor, python, runId, progress, settings, null);
    }

    static CollectionReport runBatch(List<SourceRun> sources, VacancyProcessor processor, PythonGateway python,
                                     String runId, BatchProgress progress, CollectionSettings settings,
                                     Recommendations recommendations) throws IOException {
        var scope = new ArrayList<ScopeItem>();
        var reports = new LinkedHashMap<String, CollectedSource>();
        String sourceName = sources.size() == 1 ? sources.getFirst().source().source() : "mixed";
        SourceResult result;
        CollectionReport report;
        try {
            result = collectRecommendations(sources, processor, runId, progress, settings,
                    recommendations, reports, scope);
            if (result == null || "complete".equals(result.status()))
                result = collectSearches(sources, processor, runId, progress, reports, scope);
            report = batchReport(sourceName, runId, result.status(), sources, reports, scope, settings);
        } catch (IOException | RuntimeException error) {
            python.finishRun(runId, "blocked", "java_exception",
                    sources.stream().mapToInt(entry -> entry.sink().acceptedCount()).sum(),
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        }
        python.finishRun(runId, result.status(), sources.size() > 1 && "complete".equals(result.status())
                ? "sources_complete" : result.stopReason(), report.acceptedCount(), report.message());
        return report;
    }

    private static SourceResult collectRecommendations(List<SourceRun> sources, VacancyProcessor processor,
            String runId, BatchProgress progress, CollectionSettings settings, Recommendations recommendations,
            Map<String, CollectedSource> reports, List<ScopeItem> scope) throws IOException {
        if (settings == null || !settings.sources().contains("linkedin")
                || !settings.linkedin().includeRecommendations()) return null;
        progress.email(runId, new Mailer.RecommendationStats(0, 0, 0));
        List<String> urls = recommendations.load(stats -> progress.email(runId, stats));
        if (urls.isEmpty()) return null;
        var entry = sources.stream().filter(s -> s.source().source().equals("linkedin")).findFirst().orElseThrow();
        progress.emit("linkedin", entry.source().startingSnapshot("starting", urls.size()));
        SourceResult result = entry.source().collectUrls(urls, processor, entry.sink());
        recordSource(entry, runId, result, reports, scope, progress);
        return result;
    }

    private static SourceResult collectSearches(List<SourceRun> sources, VacancyProcessor processor,
            String runId, BatchProgress progress, Map<String, CollectedSource> reports, List<ScopeItem> scope) throws IOException {
        SourceResult result = null;
        for (var entry : sources) {
            if (entry.source().source().equals("linkedin") && entry.sink().acceptedCount() >= entry.limit()) {
                result = new SourceResult("complete", "limit_reached", "");
            } else {
                progress.emit(entry.source().source(), entry.source().startingSnapshot("starting", null));
                result = entry.source().collectBatch(processor, entry.sink());
            }
            recordSource(entry, runId, result, reports, scope, progress);
            if (!"complete".equals(result.status())) break;
        }
        return result;
    }

    private static void recordSource(SourceRun entry, String runId, SourceResult result,
            Map<String, CollectedSource> reports, List<ScopeItem> scope, BatchProgress progress) {
        var source = entry.source();
        var report = entry.sink().report(source.source(), runId, result, source.pages());
        var previousState = reports.get(source.source());
        var previous = previousState == null ? null : previousState.report();
        scope.addAll(report.scope().subList(previous == null ? 0 : previous.acceptedCount(), report.acceptedCount()));
        if (previous != null && !previous.message().isBlank() && !report.message().startsWith(previous.message())) {
            String message = previous.message() + (report.message().isBlank() ? "" : "\n" + report.message());
            report = new CollectionReport(report.source(), runId, report.status(), report.acceptedCount(),
                    report.scope(), report.outcomes(), report.pages(), message);
        }
        reports.put(source.source(), new CollectedSource(report, result.stopReason()));
        progress.emit(source.source(), source.snapshot(result.status(), report.message().isBlank() ? result.stopReason() : report.message()));
    }

    private static CollectionReport batchReport(String sourceName, String runId, String status, List<SourceRun> sources,
            Map<String, CollectedSource> reports, List<ScopeItem> scope, CollectionSettings settings) {
        var outcomes = new LinkedHashMap<String, Integer>();
        var pages = new ArrayList<PageReport>();
        var results = new ArrayList<SourceCollectionResult>();
        var messages = new ArrayList<String>();
        for (var entry : sources) {
            var collected = reports.get(entry.source().source());
            if (collected == null) continue;
            var report = collected.report();
            report.outcomes().forEach((key, count) -> outcomes.merge(key, count, Integer::sum));
            pages.addAll(report.pages());
            results.add(new SourceCollectionResult(report.source(), report.status(), entry.limit(),
                    report.acceptedCount(), report.outcomes(), collected.stopReason(), report.message()));
            if (!report.message().isBlank()) messages.add((sources.size() == 1 ? "" : report.source() + ": ") + report.message());
        }
        return new CollectionReport(sourceName, runId, status, scope.size(), scope, outcomes, pages,
                String.join("\n", messages), results, settings);
    }

    public CollectionReport collectFromUrl(String runId, String url, Consumer<ProgressSnapshot> progress) throws IOException {
        return collectFromUrls(runId, List.of(url), progress);
    }

    public CollectionReport collectFromUrls(String runId, List<String> urls, Consumer<ProgressSnapshot> progress) throws IOException {
        return collect(runId, urls, progress);
    }

    private CollectionReport collect(String runId, List<String> urls, Consumer<ProgressSnapshot> progress) throws IOException {
        Objects.requireNonNull(progress);
        CollectorConfig config = CollectorConfig.load(paths.configPath());
        // Explicit URL commands retain the existing LinkedIn path, independently of batch selection.
        CollectorSource source = new LinkedInSource(paths, config, runId, progress);
        progress.accept(source.startingSnapshot("starting", urls == null ? null : urls.size()));
        try (PythonGateway python = new JpyPythonGateway(paths, pythonRuntime)) {
            python.startRun(runId, config);
            return run(source, new VacancyProcessor(python), python, new CollectionAccumulator(), runId, urls, progress);
        }
    }

    @LogOutput(message = "Collection finished", include = {"source", "runId", "status", "acceptedCount"})
    static CollectionReport run(CollectorSource source, VacancyProcessor processor, PythonGateway python,
                                CollectionAccumulator sink, String runId, List<String> urls,
                                Consumer<ProgressSnapshot> progress) throws IOException {
        try {
            SourceResult result = urls == null ? source.collectBatch(processor, sink)
                    : source.collectUrls(urls, processor, sink);
            CollectionReport report = sink.report(source.source(), runId, result, source.pages());
            progress.accept(source.snapshot(result.status(), report.message().isBlank() ? result.stopReason() : report.message()));
            python.finishRun(runId, result.status(), result.stopReason(), report.acceptedCount(), report.message());
            return report;
        } catch (IOException | RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            python.finishRun(runId, "blocked", "java_exception", sink.acceptedCount(), message);
            throw error;
        }
    }
}
