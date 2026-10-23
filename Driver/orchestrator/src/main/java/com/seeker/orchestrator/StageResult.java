package com.seeker.orchestrator;

import com.google.gson.annotations.SerializedName;
import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;

public record StageResult(
        String source,
        @SerializedName("run_id") String runId,
        String status,
        @SerializedName("processed_scope") List<ScopeItem> processedScope,
        @SerializedName("processed_count") int processedCount,
        @SerializedName("saved_count") int savedCount,
        String message
) {
    public StageResult {
        processedScope = List.copyOf(processedScope);
        processedCount = processedScope.size();
        message = message == null ? "" : message;
    }

    StageResult(String source, String runId, List<ScopeItem> processedScope) {
        this(source, runId, processedScope, 0);
    }

    StageResult(String source, String runId, List<ScopeItem> processedScope, int savedCount) {
        this(source, runId, "complete", processedScope, processedScope.size(), savedCount, "");
    }
}
