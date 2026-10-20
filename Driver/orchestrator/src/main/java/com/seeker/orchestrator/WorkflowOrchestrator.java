package com.seeker.orchestrator;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.ScopeItem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/** Top-level Java workflow entry point. */
public final class WorkflowOrchestrator {
    private final LinkedInBatch linkedinBatch;
    private final LinkedInUrls linkedinUrls;
    private final AgentFilterGateway agentFilter;
    private final int vacanciesPerAgent;

    public WorkflowOrchestrator(Path projectRoot) throws IOException {
        this(projectRoot, ignored -> { });
    }

    public WorkflowOrchestrator(Path projectRoot, Consumer<CollectionProgress> progress)
            throws IOException {
        this(
                new LinkedInApplication(projectRoot, progress),
                new JpyAgentFilterGateway(projectRoot),
                vacanciesPerAgent(projectRoot)
        );
    }

    private WorkflowOrchestrator(
            LinkedInApplication linkedin,
            AgentFilterGateway agentFilter,
            int vacanciesPerAgent
    ) {
        this(linkedin::batch, linkedin::fromUrls, agentFilter, vacanciesPerAgent);
    }

    WorkflowOrchestrator(
            LinkedInBatch linkedinBatch,
            AgentFilterGateway agentFilter
    ) {
        this(linkedinBatch, ignored -> linkedinBatch.collect(), agentFilter);
    }

    WorkflowOrchestrator(
            LinkedInBatch linkedinBatch,
            LinkedInUrls linkedinUrls,
            AgentFilterGateway agentFilter
    ) {
        this(linkedinBatch, linkedinUrls, agentFilter, Integer.MAX_VALUE);
    }

    private WorkflowOrchestrator(
            LinkedInBatch linkedinBatch,
            LinkedInUrls linkedinUrls,
            AgentFilterGateway agentFilter,
            int vacanciesPerAgent
    ) {
        this.linkedinBatch = Objects.requireNonNull(linkedinBatch);
        this.linkedinUrls = Objects.requireNonNull(linkedinUrls);
        this.agentFilter = Objects.requireNonNull(agentFilter);
        this.vacanciesPerAgent = vacanciesPerAgent;
    }

    public WorkflowResult batch(String source) throws IOException {
        if (!"linkedin".equals(source)) {
            throw new IllegalArgumentException("Unsupported batch source: " + source);
        }
        return processCollected(linkedinBatch.collect());
    }

    public WorkflowResult check(String... urls) throws IOException {
        WorkflowResult report = processCollected(linkedinUrls.collect(List.of(urls)));
        int agentFiltered = report.agentFilteredCount();
        if (agentFiltered > 0) {
            String reason = urls.length == 1
                    ? "Vacancy rejected by agent relevance filter"
                    : "Vacancies rejected by agent relevance filter: " + agentFiltered;
            return report.withMessage(report.message().isEmpty()
                            ? reason : report.message() + "; " + reason
            );
        }
        return report;
    }

    public WorkflowResult resume(String runId) {
        List<ScopeItem> scope = agentFilter.collectedScope(runId);
        return processScope("linkedin", runId, scope, null);
    }

    private WorkflowResult processCollected(CollectionReport collected) {
        if (!"complete".equals(collected.status()) || collected.scope().isEmpty()) {
            return new WorkflowResult(collected.source(), collected.runId(), collected.status(),
                    collected, List.of(), 0, collected.message());
        }
        return processScope(collected.source(), collected.runId(), collected.scope(), collected);
    }

    private WorkflowResult processScope(String source, String runId, List<ScopeItem> scope,
                                        CollectionReport collected) {
        Map<String, String> statuses = new HashMap<>(
                agentFilter.statuses(source, scope)
        );
        List<ScopeItem> unfiltered = atStatus(scope, statuses, "CLEANED");
        if (!unfiltered.isEmpty()) {
            AgentFilterResult result = agentFilter.filter(runId, unfiltered);
            List<String> nonrelevantIds = validateResult(
                    unfiltered, result.nonrelevantJobIds()
            );
            agentFilter.markRelevance(source, unfiltered, nonrelevantIds);
            Set<String> rejected = Set.copyOf(nonrelevantIds);
            for (ScopeItem item : unfiltered) {
                statuses.put(item.jobId(), rejected.contains(item.jobId())
                        ? "NONRELEVANT" : "RELEVANT");
            }
        }

        Set<String> acceptedStatuses = Set.of("RELEVANT", "ANALYZED", "SCORED", "SAVED");
        List<ScopeItem> remaining = scope.stream()
                .filter(item -> acceptedStatuses.contains(statuses.get(item.jobId())))
                .toList();
        List<ScopeItem> facts = atStatus(remaining, statuses, "RELEVANT");
        if (!facts.isEmpty() || !atStatus(remaining, statuses, "ANALYZED").isEmpty()) {
            agentFilter.startAnalysis(runId, vacanciesPerAgent);
        }
        runJobFacts(runId, source, facts);
        for (ScopeItem item : facts) {
            statuses.put(item.jobId(), "ANALYZED");
        }
        runFinishAnalysis(runId, source,
                atStatus(remaining, statuses, "ANALYZED"));
        List<ScopeItem> scored = atStatus(remaining, statuses, "SCORED");
        if (!scored.isEmpty()) {
            agentFilter.saveScored(source, scored.stream().map(ScopeItem::jobId).toList());
        }
        return new WorkflowResult(source, runId, "complete", collected, remaining,
                atStatus(scope, statuses, "NONRELEVANT").size(),
                collected == null ? "" : collected.message());
    }

    public void jobFacts(String runId, String source, List<ScopeItem> scope) {
        agentFilter.startAnalysis(runId, vacanciesPerAgent);
        runJobFacts(runId, source, scope);
    }

    public void finishAnalysis(
            String runId,
            String source,
            List<ScopeItem> scope
    ) {
        agentFilter.startAnalysis(runId, vacanciesPerAgent);
        runFinishAnalysis(runId, source, scope);
    }

    private void runJobFacts(String runId, String source, List<ScopeItem> scope) {
        // TODO: process groups in parallel using parallel_agents.
        for (int start = 0, group = 1;
             start < scope.size();
             start += vacanciesPerAgent, group++) {
            String target = source + ":job_facts:group-" + group;
            String threadId = null;
            for (ScopeItem item : scope.subList(
                    start,
                    Math.min(start + vacanciesPerAgent, scope.size())
            )) {
                System.err.println("Job facts start: job_id=" + item.jobId());
                long started = System.nanoTime();
                threadId = agentFilter.jobFacts(
                        runId, source, item.jobId(), target, threadId
                );
                System.err.printf(
                        "Job facts complete: job_id=%s duration_ms=%d%n",
                        item.jobId(),
                        (System.nanoTime() - started) / 1_000_000
                );
            }
        }
    }

    private void runFinishAnalysis(
            String runId,
            String source,
            List<ScopeItem> scope
    ) {
        if (scope.isEmpty()) {
            return;
        }
        String threadId = null;
        String target = source + ":candidate_fit";
        for (ScopeItem item : scope) {
            threadId = agentFilter.candidateFit(
                    runId, source, item.jobId(), target, threadId
            );
            agentFilter.jobInterest(source, item.jobId());
        }
        agentFilter.saveScored(
                source,
                scope.stream().map(ScopeItem::jobId).toList()
        );
    }

    private static List<ScopeItem> atStatus(
            List<ScopeItem> scope, Map<String, String> statuses, String status
    ) {
        return scope.stream()
                .filter(item -> status.equals(statuses.get(item.jobId())))
                .toList();
    }

    private static int vacanciesPerAgent(Path projectRoot) throws IOException {
        Path config = projectRoot.toAbsolutePath().normalize()
                .resolve("Driver/analyzer/config/execution.ini");
        for (String rawLine : Files.readAllLines(config)) {
            String line = rawLine.strip();
            if (line.startsWith("vacancies_per_agent")) {
                int value = Integer.parseInt(
                        line.substring(line.indexOf('=') + 1).strip()
                );
                if (value > 0) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException(
                "Missing positive vacancies_per_agent in " + config
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

    @FunctionalInterface
    interface LinkedInUrls {
        CollectionReport collect(List<String> urls) throws IOException;
    }
}
