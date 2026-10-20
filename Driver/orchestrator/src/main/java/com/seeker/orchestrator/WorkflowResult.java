package com.seeker.orchestrator;

import com.google.gson.annotations.SerializedName;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;

/** Workflow output; the optional collection report keeps its original meaning. */
public record WorkflowResult(
        String source,
        @SerializedName("run_id") String runId,
        String status,
        CollectionReport collection,
        @SerializedName("processed_scope") List<ScopeItem> processedScope,
        @SerializedName("agent_filtered_count") int agentFilteredCount,
        String message
) {
    public WorkflowResult {
        processedScope = List.copyOf(processedScope);
        message = message == null ? "" : message;
    }

    public int processedCount() {
        return processedScope.size();
    }

    WorkflowResult withMessage(String value) {
        return new WorkflowResult(source, runId, status, collection, processedScope,
                agentFilteredCount, value);
    }
}
