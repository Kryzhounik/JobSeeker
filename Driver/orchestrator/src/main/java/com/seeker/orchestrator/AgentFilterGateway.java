package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;

interface AgentFilterGateway {
    AgentFilterResult filter(String runId, List<ScopeItem> scope);

    void markNonrelevant(String source, List<String> jobIds);

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
