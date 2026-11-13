package com.seeker.collector.core;

import com.seeker.collector.common.*;
import com.seeker.collector.common.python.*;
import com.seeker.collector.linkedin.collection.LinkedInSource;
import com.seeker.collector.linkedin.config.*;
import com.seeker.python.PythonRuntime;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
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
        return collect(runId, null, progress);
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
        // Stage 1 keeps the existing LinkedIn-only entry points and workflow.
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
