package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;
import java.util.Map;

interface ProcessingGateway {
    List<ScopeItem> loadCollectedScope(String runId);

    Map<String, String> loadStatuses(String source, List<ScopeItem> scope);

    AgentFilterResult runRelevanceFilter(String runId, List<ScopeItem> scope);

    void markRelevance(String source, List<ScopeItem> scope, List<String> nonrelevantIds);

    void initializeAnalysisRun(String runId, int vacanciesPerAgent);

    String extractJobFacts(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    );

    String evaluateCandidateFit(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    );

    void calculateJobInterest(String source, String jobId);

    void saveScored(String source, List<String> jobIds);
}
