package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.ScopeItem;

import java.util.List;

interface AgentFilterGateway {
    AgentFilterResult filter(String runId, List<ScopeItem> scope);

    void markNonrelevant(String source, List<String> jobIds);
}
