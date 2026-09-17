package com.seeker.orchestrator;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Top-level Java workflow entry point. */
public final class WorkflowOrchestrator {
    private final LinkedInBatch linkedinBatch;
    private final AgentFilterGateway agentFilter;

    public WorkflowOrchestrator(Path projectRoot) throws IOException {
        this(
                new LinkedInApplication(projectRoot)::batch,
                new JpyAgentFilterGateway(projectRoot)
        );
    }

    WorkflowOrchestrator(
            LinkedInBatch linkedinBatch,
            AgentFilterGateway agentFilter
    ) {
        this.linkedinBatch = Objects.requireNonNull(linkedinBatch);
        this.agentFilter = Objects.requireNonNull(agentFilter);
    }

    public CollectionReport batch(String source) throws IOException {
        if (!"linkedin".equals(source)) {
            throw new IllegalArgumentException("Unsupported batch source: " + source);
        }
        CollectionReport collected = linkedinBatch.collect();
        if (!"complete".equals(collected.status()) || collected.scope().isEmpty()) {
            return collected;
        }

        AgentFilterResult result = agentFilter.filter(
                collected.runId(),
                collected.scope()
        );
        List<String> nonrelevantIds = validateResult(
                collected.scope(),
                result.nonrelevantJobIds()
        );
        if (!nonrelevantIds.isEmpty()) {
            agentFilter.markNonrelevant(collected.source(), nonrelevantIds);
        }

        Set<String> rejected = Set.copyOf(nonrelevantIds);
        List<ScopeItem> remaining = collected.scope().stream()
                .filter(item -> !rejected.contains(item.jobId()))
                .toList();
        Map<String, Integer> outcomes = new LinkedHashMap<>(collected.outcomes());
        outcomes.put("agent_filtered", nonrelevantIds.size());

        return new CollectionReport(
                collected.source(),
                collected.runId(),
                collected.status(),
                remaining.size(),
                remaining,
                Map.copyOf(outcomes),
                collected.pages(),
                collected.message()
        );
    }

    private static List<String> validateResult(
            List<ScopeItem> scope,
            List<String> nonrelevantIds
    ) {
        if (nonrelevantIds == null) {
            throw new IllegalArgumentException(
                    "Agent filter response is missing nonrelevant_job_ids"
            );
        }

        Map<String, Integer> scopeOrder = new HashMap<>();
        for (int index = 0; index < scope.size(); index++) {
            String jobId = scope.get(index).jobId();
            if (scopeOrder.put(jobId, index) != null) {
                throw new IllegalArgumentException(
                        "Collected scope contains duplicate job ID: " + jobId
                );
            }
        }

        List<String> validated = new ArrayList<>(nonrelevantIds.size());
        Set<String> seen = new HashSet<>();
        int previousIndex = -1;
        for (String jobId : nonrelevantIds) {
            if (jobId == null || jobId.isBlank()) {
                throw new IllegalArgumentException(
                        "Agent filter returned an empty job ID"
                );
            }
            Integer index = scopeOrder.get(jobId);
            if (index == null) {
                throw new IllegalArgumentException(
                        "Agent filter returned job ID outside scope: " + jobId
                );
            }
            if (!seen.add(jobId)) {
                throw new IllegalArgumentException(
                        "Agent filter returned duplicate job ID: " + jobId
                );
            }
            if (index <= previousIndex) {
                throw new IllegalArgumentException(
                        "Agent filter did not preserve scope order"
                );
            }
            previousIndex = index;
            validated.add(jobId);
        }
        return List.copyOf(validated);
    }

    @FunctionalInterface
    interface LinkedInBatch {
        CollectionReport collect() throws IOException;
    }
}
