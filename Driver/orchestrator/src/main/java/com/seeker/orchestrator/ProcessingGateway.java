package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.codex.UsageLimitExceededException;

import java.util.List;
import java.util.Map;

interface ProcessingGateway {
    void updateBatch(String runId, String status, String stage, String message);

    List<ScopeItem> loadCollectedScope(String runId);

    Map<String, String> loadStatuses(String source, List<ScopeItem> scope);

    AgentFilterResult runRelevanceFilter(String runId, List<ScopeItem> scope) throws UsageLimitExceededException;

    void markRelevance(String source, List<ScopeItem> scope, List<String> nonrelevantIds);

    void initializeAnalysisRun(String runId, int parallelAgents, int vacanciesPerAgent);

    String extractJobFacts(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) throws UsageLimitExceededException;

    String evaluateCandidateFit(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    ) throws UsageLimitExceededException;

    void calculateJobInterest(String source, String jobId);

    int saveScored(String source, List<String> jobIds);
}
