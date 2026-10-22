package com.seeker.orchestrator;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.mailer.Mailer;

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
    private final LinkedInCollection linkedinCollection;
    private final LinkedInUrls linkedinUrls;
    private final ProcessingGateway processingGateway;
    private final RecommendationSource recommendations;
    private final int vacanciesPerAgent;

    public WorkflowOrchestrator(Path projectRoot) throws IOException {
        this(
                new LinkedInApplication(projectRoot),
                new JpyProcessingGateway(projectRoot),
                new Mailer(projectRoot)::getRecommendationUrls,
                vacanciesPerAgent(projectRoot)
        );
    }

    private WorkflowOrchestrator(
            LinkedInApplication linkedin,
            ProcessingGateway processingGateway,
            RecommendationSource recommendations,
            int vacanciesPerAgent
    ) {
        this(linkedin::collectBatch, linkedin::collectUrls, processingGateway,
                recommendations, vacanciesPerAgent);
    }

    WorkflowOrchestrator(
            LinkedInCollection linkedinCollection,
            ProcessingGateway processingGateway
    ) {
        this(linkedinCollection, (urls, prefix, progress) -> linkedinCollection.collect(progress), processingGateway);
    }

    WorkflowOrchestrator(
            LinkedInCollection linkedinCollection,
            LinkedInUrls linkedinUrls,
            ProcessingGateway processingGateway
    ) {
        this(linkedinCollection, linkedinUrls, processingGateway, List::of);
    }

    WorkflowOrchestrator(
            LinkedInCollection linkedinCollection,
            LinkedInUrls linkedinUrls,
            ProcessingGateway processingGateway,
            RecommendationSource recommendations
    ) {
        this(linkedinCollection, linkedinUrls, processingGateway, recommendations, Integer.MAX_VALUE);
    }

    private WorkflowOrchestrator(
            LinkedInCollection linkedinCollection,
            LinkedInUrls linkedinUrls,
            ProcessingGateway processingGateway,
            RecommendationSource recommendations,
            int vacanciesPerAgent
    ) {
        this.linkedinCollection = Objects.requireNonNull(linkedinCollection);
        this.linkedinUrls = Objects.requireNonNull(linkedinUrls);
        this.processingGateway = Objects.requireNonNull(processingGateway);
        this.recommendations = Objects.requireNonNull(recommendations);
        this.vacanciesPerAgent = vacanciesPerAgent;
    }

    public WorkflowResult processBatch(String source) throws IOException, UsageLimitExceededException {
        return processBatch(source, ignored -> { });
    }

    public WorkflowResult processBatch(String source, Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        if (!"linkedin".equals(source)) {
            throw new IllegalArgumentException("Unsupported process source: " + source);
        }
        return processCollected(linkedinCollection.collect(collectionHandler(progress)), progress);
    }

    public WorkflowResult processUrls(String... urls) throws IOException, UsageLimitExceededException {
        return processUrls(List.of(urls), ignored -> { });
    }

    public WorkflowResult processUrls(List<String> urls, Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        return processUrls(urls, "", progress);
    }

    public WorkflowResult checkRecommendations() throws IOException, UsageLimitExceededException {
        return checkRecommendations(ignored -> { });
    }

    public WorkflowResult checkRecommendations(Consumer<WorkflowProgress> progress)
            throws IOException, UsageLimitExceededException {
        List<String> urls = recommendations.load();
        if (urls.isEmpty()) {
            return new WorkflowResult("linkedin", null, "complete", null,
                    List.of(), 0, "No new recommendations");
        }
        return processUrls(urls, "recommendations", progress);
    }

    private WorkflowResult processUrls(List<String> urls, String runPrefix,
                                       Consumer<WorkflowProgress> progress) throws IOException, UsageLimitExceededException {
        WorkflowResult report = processCollected(
                linkedinUrls.collect(urls, runPrefix, collectionHandler(progress)), progress);
        int agentFiltered = report.agentFilteredCount();
        if (agentFiltered > 0) {
            String reason = urls.size() == 1
                    ? "Vacancy rejected by agent relevance filter"
                    : "Vacancies rejected by agent relevance filter: " + agentFiltered;
            return report.withMessage(report.message().isEmpty()
                            ? reason : report.message() + "; " + reason
            );
        }
        return report;
    }

    public WorkflowResult resume(String runId) throws UsageLimitExceededException {
        return resume(runId, ignored -> { });
    }

    public WorkflowResult resume(String runId, Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        List<ScopeItem> scope = processingGateway.loadCollectedScope(runId);
        return processScope("linkedin", runId, scope, null, progress);
    }

    private WorkflowResult processCollected(CollectionReport collected, Consumer<WorkflowProgress> progress)
            throws UsageLimitExceededException {
        if (!"complete".equals(collected.status()) || collected.scope().isEmpty()) {
            return new WorkflowResult(collected.source(), collected.runId(), collected.status(),
                    collected, List.of(), 0, collected.message());
        }
        return processScope(collected.source(), collected.runId(), collected.scope(), collected, progress);
    }

    private WorkflowResult processScope(String source, String runId, List<ScopeItem> scope,
                                        CollectionReport collected, Consumer<WorkflowProgress> progress)
            throws UsageLimitExceededException {
        reportProgress(progress, runId, "loading_statuses", 0, scope.size(), "");
        Map<String, String> statuses = new HashMap<>(
                processingGateway.loadStatuses(source, scope)
        );
        List<ScopeItem> unfiltered = atStatus(scope, statuses, "CLEANED");
        if (!unfiltered.isEmpty()) {
            reportProgress(progress, runId, "relevance_filter", 0, unfiltered.size(), "");
            AgentFilterResult result = processingGateway.runRelevanceFilter(runId, unfiltered);
            List<String> nonrelevantIds = validateResult(
                    unfiltered, result.nonrelevantJobIds()
            );
            processingGateway.markRelevance(source, unfiltered, nonrelevantIds);
            Set<String> rejected = Set.copyOf(nonrelevantIds);
            for (ScopeItem item : unfiltered) {
                statuses.put(item.jobId(), rejected.contains(item.jobId())
                        ? "NONRELEVANT" : "RELEVANT");
            }
            progress.accept(new WorkflowProgress(runId, "relevance_filter", unfiltered.size(),
                    unfiltered.size(), "", nonrelevantIds.size(), "Relevance filtering complete."));
        }

        Set<String> acceptedStatuses = Set.of("RELEVANT", "ANALYZED", "SCORED", "SAVED");
        List<ScopeItem> remaining = scope.stream()
                .filter(item -> acceptedStatuses.contains(statuses.get(item.jobId())))
                .toList();
        List<ScopeItem> facts = atStatus(remaining, statuses, "RELEVANT");
        if (!facts.isEmpty() || !atStatus(remaining, statuses, "ANALYZED").isEmpty()) {
            reportProgress(progress, runId, "initializing_analysis", 0, remaining.size(), "");
            processingGateway.initializeAnalysisRun(runId, vacanciesPerAgent);
        }
        runJobFacts(runId, source, facts, progress);
        for (ScopeItem item : facts) {
            statuses.put(item.jobId(), "ANALYZED");
        }
        runFinishAnalysis(runId, source,
                atStatus(remaining, statuses, "ANALYZED"), progress);
        List<ScopeItem> scored = atStatus(remaining, statuses, "SCORED");
        if (!scored.isEmpty()) {
            reportProgress(progress, runId, "saving", 0, scored.size(), "");
            processingGateway.saveScored(source, scored.stream().map(ScopeItem::jobId).toList());
            reportProgress(progress, runId, "saving", scored.size(), scored.size(), "");
        }
        reportProgress(progress, runId, "complete", remaining.size(), remaining.size(), "");
        return new WorkflowResult(source, runId, "complete", collected, remaining,
                atStatus(scope, statuses, "NONRELEVANT").size(),
                collected == null ? "" : collected.message());
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return jobFacts(runId, source, scope, ignored -> { });
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope,
                                Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        processingGateway.initializeAnalysisRun(runId, vacanciesPerAgent);
        runJobFacts(runId, source, scope, progress);
        return new StageResult(source, runId, scope);
    }

    public StageResult finishAnalysis(
            String runId,
            String source,
            List<ScopeItem> scope
    ) throws UsageLimitExceededException {
        return finishAnalysis(runId, source, scope, ignored -> { });
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope,
                                      Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        processingGateway.initializeAnalysisRun(runId, vacanciesPerAgent);
        runFinishAnalysis(runId, source, scope, progress);
        return new StageResult(source, runId, scope);
    }

    private void runJobFacts(String runId, String source, List<ScopeItem> scope,
                              Consumer<WorkflowProgress> progress) throws UsageLimitExceededException {
        int completed = 0;
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
                reportProgress(progress, runId, "job_facts", completed, scope.size(), item.jobId());
                System.err.println("Job facts start: job_id=" + item.jobId());
                long started = System.nanoTime();
                threadId = processingGateway.extractJobFacts(
                        runId, source, item.jobId(), target, threadId
                );
                reportProgress(progress, runId, "job_facts", ++completed, scope.size(), item.jobId());
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
            List<ScopeItem> scope,
            Consumer<WorkflowProgress> progress
    ) throws UsageLimitExceededException {
        if (scope.isEmpty()) {
            return;
        }
        String threadId = null;
        String target = source + ":candidate_fit";
        int completed = 0;
        for (ScopeItem item : scope) {
            reportProgress(progress, runId, "candidate_fit", completed, scope.size(), item.jobId());
            threadId = processingGateway.evaluateCandidateFit(
                    runId, source, item.jobId(), target, threadId
            );
            reportProgress(progress, runId, "job_interest", completed, scope.size(), item.jobId());
            processingGateway.calculateJobInterest(source, item.jobId());
            reportProgress(progress, runId, "job_interest", ++completed, scope.size(), item.jobId());
        }
        reportProgress(progress, runId, "saving", 0, scope.size(), "");
        processingGateway.saveScored(
                source,
                scope.stream().map(ScopeItem::jobId).toList()
        );
        reportProgress(progress, runId, "saving", scope.size(), scope.size(), "");
    }

    private static void reportProgress(Consumer<WorkflowProgress> progress,
                                       String runId, String stage, int completed, int total, String jobId) {
        progress.accept(new WorkflowProgress(runId, stage, completed, total, jobId, 0, ""));
    }

    private static Consumer<CollectionProgress> collectionHandler(Consumer<WorkflowProgress> progress) {
        Objects.requireNonNull(progress);
        return update -> progress.accept(new WorkflowProgress(update.runId(), "collection",
                update.acceptedCount(), update.limit(), "", 0,
                update.stage() + (update.message().isEmpty() ? "" : ": " + update.message())));
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
    interface LinkedInCollection {
        CollectionReport collect(Consumer<CollectionProgress> progress) throws IOException;
    }

    @FunctionalInterface
    interface LinkedInUrls {
        CollectionReport collect(List<String> urls, String runPrefix, Consumer<CollectionProgress> progress)
                throws IOException;
    }

    @FunctionalInterface
    interface RecommendationSource {
        List<String> load() throws IOException;
    }
}
