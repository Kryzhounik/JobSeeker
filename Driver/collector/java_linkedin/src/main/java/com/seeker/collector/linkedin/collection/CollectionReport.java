package com.seeker.collector.linkedin.collection;

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
        String message
) {
    public CollectionReport {
        scope = scope == null ? List.of() : List.copyOf(scope);
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
        pages = pages == null ? List.of() : List.copyOf(pages);
        message = message == null ? "" : message;
    }
}
