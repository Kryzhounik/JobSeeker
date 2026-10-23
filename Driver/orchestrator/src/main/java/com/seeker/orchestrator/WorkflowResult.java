package com.seeker.orchestrator;

import com.google.gson.annotations.SerializedName;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;
import java.util.Map;

/** Workflow output with an optional collection summary, excluding page diagnostics. */
public record WorkflowResult(
        String source,
        @SerializedName("run_id") String runId,
        String status,
        CollectionSummary collection,
        @SerializedName("processed_scope") List<ScopeItem> processedScope,
        @SerializedName("processed_count") int processedCount,
        @SerializedName("agent_filtered_count") int agentFilteredCount,
        @SerializedName("saved_count") int savedCount,
        String message
) {
    public WorkflowResult {
        processedScope = List.copyOf(processedScope);
        processedCount = processedScope.size();
        message = message == null ? "" : message;
    }

    public WorkflowResult(
            String source, String runId, String status, CollectionReport collection,
            List<ScopeItem> processedScope, int agentFilteredCount, String message
    ) {
        this(source, runId, status, collection, processedScope, agentFilteredCount, 0, message);
    }

    public WorkflowResult(
            String source, String runId, String status, CollectionReport collection,
            List<ScopeItem> processedScope, int agentFilteredCount, int savedCount, String message
    ) {
        this(source, runId, status, CollectionSummary.from(collection), processedScope,
                processedScope.size(), agentFilteredCount, savedCount, message);
    }

    WorkflowResult withMessage(String value) {
        return new WorkflowResult(source, runId, status, collection, processedScope,
                processedCount, agentFilteredCount, savedCount, value);
    }

    public record CollectionSummary(
            String source,
            @SerializedName("run_id") String runId,
            String status,
            @SerializedName("accepted_count") int acceptedCount,
            List<ScopeItem> scope,
            Map<String, Integer> outcomes,
            String message
    ) {
        public CollectionSummary {
            scope = List.copyOf(scope);
            outcomes = Map.copyOf(outcomes);
        }

        private static CollectionSummary from(CollectionReport report) {
            return report == null ? null : new CollectionSummary(
                    report.source(), report.runId(), report.status(), report.acceptedCount(),
                    report.scope(), report.outcomes(), report.message()
            );
        }
    }
}
