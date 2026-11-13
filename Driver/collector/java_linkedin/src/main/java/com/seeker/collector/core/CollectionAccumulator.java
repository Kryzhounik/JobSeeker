package com.seeker.collector.core;

import com.seeker.collector.common.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CollectionAccumulator implements CollectionSink {
    private final OutcomeCounter outcomes = new OutcomeCounter();
    private final List<ScopeItem> scope = new ArrayList<>();
    private final List<String> failures = new ArrayList<>();

    @Override public void record(ProcessResult result) {
        outcomes.add(result.status());
        if (result.accepted()) scope.add(new ScopeItem(result.source(), result.jobId(), result.title(), result.fields()));
        if (List.of("open_failed", "processing_failed", "incomplete_raw").contains(result.status())) {
            failures.add(result.jobId() + ": " + result.reason());
        }
    }

    @Override public int acceptedCount() { return scope.size(); }
    @Override public Map<String, Integer> outcomes() { return outcomes.snapshot(); }

    public CollectionReport report(String source, String runId, SourceResult result, List<PageReport> pages) {
        return new CollectionReport(source, runId, result.status(), scope.size(), scope, outcomes(), pages,
                result.message().isBlank() ? String.join("\n", failures) : result.message());
    }
}
