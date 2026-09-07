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
    static CollectionReport complete(
            String runId,
            List<ScopeItem> scope,
            Map<String, Integer> outcomes,
            List<PageReport> pages
    ) {
        return new CollectionReport(
                "linkedin",
                runId,
                "complete",
                scope.size(),
                List.copyOf(scope),
                Map.copyOf(outcomes),
                List.copyOf(pages),
                ""
        );
    }

    static CollectionReport blocked(
            String runId,
            List<ScopeItem> scope,
            Map<String, Integer> outcomes,
            List<PageReport> pages,
            String message
    ) {
        return halted(runId, "blocked", scope, outcomes, pages, message);
    }

    static CollectionReport halted(
            String runId,
            String status,
            List<ScopeItem> scope,
            Map<String, Integer> outcomes,
            List<PageReport> pages,
            String message
    ) {
        return new CollectionReport(
                "linkedin",
                runId,
                status,
                scope.size(),
                List.copyOf(scope),
                Map.copyOf(outcomes),
                List.copyOf(pages),
                message
        );
    }

}
