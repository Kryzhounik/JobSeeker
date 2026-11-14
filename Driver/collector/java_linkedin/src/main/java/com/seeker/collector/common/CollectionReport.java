package com.seeker.collector.common;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

public record CollectionReport(
        String source,
        @SerializedName("run_id") String runId,
        String status,
        @SerializedName("accepted_count") int acceptedCount,
        List<ScopeItem> scope,
        Map<String, Integer> outcomes,
        List<PageReport> pages,
        String message,
        @SerializedName("source_results") List<SourceCollectionResult> sourceResults
) {
    public CollectionReport {
        scope = scope == null ? List.of() : List.copyOf(scope);
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
        pages = pages == null ? List.of() : List.copyOf(pages);
        message = message == null ? "" : message;
        sourceResults = sourceResults == null ? List.of() : List.copyOf(sourceResults);
    }

    public CollectionReport(String source, String runId, String status, int acceptedCount,
                            List<ScopeItem> scope, Map<String, Integer> outcomes, List<PageReport> pages, String message) {
        this(source, runId, status, acceptedCount, scope, outcomes, pages, message, List.of());
    }
}
