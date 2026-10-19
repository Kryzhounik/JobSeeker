package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;
import java.util.Map;

interface AgentFilterGateway {
    List<ScopeItem> collectedScope(String runId);

    Map<String, String> statuses(String source, List<ScopeItem> scope);

    AgentFilterResult filter(String runId, List<ScopeItem> scope);

    void markRelevance(String source, List<ScopeItem> scope, List<String> nonrelevantIds);

    void startAnalysis(String runId, int vacanciesPerAgent);

    String jobFacts(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    );

    String candidateFit(
            String runId,
            String source,
            String jobId,
            String target,
            String threadId
    );

    void jobInterest(String source, String jobId);

    void saveScored(String source, List<String> jobIds);
}
