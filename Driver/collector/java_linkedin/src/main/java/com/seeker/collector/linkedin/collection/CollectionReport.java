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
}
