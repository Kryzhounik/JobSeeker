package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.*;
import com.seeker.collector.linkedin.collection.LinkedInSource;
import com.seeker.collector.linkedin.config.*;
import com.seeker.collector.justjoin.JustJoinConfig;
import com.seeker.collector.justjoin.JustJoinSource;
import com.seeker.python.PythonRuntime;
import com.seeker.tools.api.ProgressSnapshot;
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
        return collectBatch(runId, null, progress);
    }

    public CollectionReport collectBatch(String runId, String selectedSource, Consumer<ProgressSnapshot> progress) throws IOException {
        List<String> names = selectedSource == null ? CollectorSettings.loadSources(paths.collectorRoot())
                : CollectorSettings.sources(selectedSource);
        runId += "-" + String.join("-", names);
        var configs = new LinkedHashMap<String, Object>();
        var limits = new LinkedHashMap<String, Integer>();
        var sinks = new LinkedHashMap<String, CollectionAccumulator>();
        for (String name : names) {
            if (name.equals("linkedin")) {
                var config = CollectorConfig.load(paths.configPath());
                configs.put(name, config); limits.put(name, config.limit());
            } else {
                var config = JustJoinConfig.load(paths.driverRoot().getParent());
                configs.put(name, config); limits.put(name, config.limit());
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
            return runBatch(sources, new VacancyProcessor(python), python, runId, batchProgress);
        }
    }

    record SourceRun(CollectorSource source, int limit, CollectionAccumulator sink) { }

    static CollectionReport runBatch(List<SourceRun> sources, VacancyProcessor processor, PythonGateway python,
                                     String runId, BatchProgress progress) throws IOException {
        var scope = new ArrayList<ScopeItem>();
        var outcomes = new LinkedHashMap<String, Integer>();
        var pages = new ArrayList<PageReport>();
        var results = new ArrayList<SourceCollectionResult>();
        var messages = new ArrayList<String>();
        String sourceName = sources.size() == 1 ? sources.getFirst().source().source() : "mixed";
        try {
            SourceResult result = null;
            for (var entry : sources) {
                var source = entry.source();
                progress.emit(source.source(), source.startingSnapshot("starting", null));
                result = source.collectBatch(processor, entry.sink());
                var report = entry.sink().report(source.source(), runId, result, source.pages());
                scope.addAll(report.scope());
                report.outcomes().forEach((key, count) -> outcomes.merge(key, count, Integer::sum));
                pages.addAll(report.pages());
                results.add(new SourceCollectionResult(source.source(), result.status(), entry.limit(),
                        report.acceptedCount(), report.outcomes(), result.stopReason(), report.message()));
                if (!report.message().isBlank()) messages.add((sources.size() == 1 ? "" : source.source() + ": ") + report.message());
                progress.emit(source.source(), source.snapshot(result.status(), report.message().isBlank() ? result.stopReason() : report.message()));
                if (!"complete".equals(result.status())) break;
            }
            var report = new CollectionReport(sourceName, runId, result.status(), scope.size(), scope, outcomes,
                    pages, String.join("\n", messages), results);
            python.finishRun(runId, result.status(), sources.size() > 1 && "complete".equals(result.status())
                    ? "sources_complete" : result.stopReason(), report.acceptedCount(), report.message());
            return report;
        } catch (IOException | RuntimeException error) {
            python.finishRun(runId, "blocked", "java_exception", 0,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
            throw error;
        }
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

    static CollectionReport run(CollectorSource source, VacancyProcessor processor, PythonGateway python,
                                CollectionAccumulator sink, String runId, List<String> urls,
                                Consumer<ProgressSnapshot> progress) throws IOException {
        try {
            SourceResult result = urls == null ? source.collectBatch(processor, sink)
                    : source.collectUrls(urls, processor, sink);
            CollectionReport report = sink.report(source.source(), runId, result, source.pages());
            progress.accept(source.snapshot(result.status(), report.message().isBlank() ? result.stopReason() : report.message()));
            python.finishRun(runId, result.status(), result.stopReason(), report.acceptedCount(), report.message());
            System.err.printf("%s collection finished: run_id=%s status=%s accepted=%d stop_reason=%s%n",
                    source.source(), runId, result.status(), report.acceptedCount(), result.stopReason());
            return report;
        } catch (IOException | RuntimeException error) {
            String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
            python.finishRun(runId, "blocked", "java_exception", 0, message);
            throw error;
        }
    }
}
