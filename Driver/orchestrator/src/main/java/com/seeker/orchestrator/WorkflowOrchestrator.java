package com.seeker.orchestrator;

import com.seeker.collector.linkedin.application.LinkedInApplication;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.tools.api.ProgressSnapshot;
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
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Top-level Java workflow entry point. */
public final class WorkflowOrchestrator {
    private final LinkedInCollection linkedinCollection;
    private final LinkedInUrls linkedinUrls;
    private final ProcessingGateway processingGateway;
    private final RecommendationSource recommendations;
    private final int parallelAgents;
    private final int vacanciesPerAgent;

    public WorkflowOrchestrator(Path projectRoot) throws IOException {
        this(
                new LinkedInApplication(projectRoot),
                new JpyProcessingGateway(projectRoot),
                new Mailer(projectRoot)::getRecommendationUrls,
                executionSetting(projectRoot, "parallel_agents"),
                executionSetting(projectRoot, "vacancies_per_agent")
        );
    }

    private WorkflowOrchestrator(
            LinkedInApplication linkedin,
            ProcessingGateway processingGateway,
            RecommendationSource recommendations,
            int parallelAgents,
            int vacanciesPerAgent
    ) {
        this(linkedin::collectBatch, linkedin::collectUrls, processingGateway,
                recommendations, parallelAgents, vacanciesPerAgent);
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
        this(linkedinCollection, linkedinUrls, processingGateway, recommendations, 1, Integer.MAX_VALUE);
    }

    WorkflowOrchestrator(
            LinkedInCollection linkedinCollection,
            LinkedInUrls linkedinUrls,
            ProcessingGateway processingGateway,
            RecommendationSource recommendations,
            int parallelAgents,
            int vacanciesPerAgent
    ) {
        this.linkedinCollection = Objects.requireNonNull(linkedinCollection);
        this.linkedinUrls = Objects.requireNonNull(linkedinUrls);
        this.processingGateway = Objects.requireNonNull(processingGateway);
        this.recommendations = Objects.requireNonNull(recommendations);
        if (parallelAgents <= 0 || vacanciesPerAgent <= 0) {
            throw new IllegalArgumentException("Analysis settings must be positive");
        }
        this.parallelAgents = parallelAgents;
        this.vacanciesPerAgent = vacanciesPerAgent;
    }

    public WorkflowResult processBatch(String source) throws IOException, UsageLimitExceededException {
        return processBatch(source, ignored -> { });
    }

    public WorkflowResult processBatch(String source, Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        if (!"linkedin".equals(source)) {
            throw new IllegalArgumentException("Unsupported process source: " + source);
        }
        WorkflowProgress.Run run = new WorkflowProgress.Run(progress);
        try {
            return processCollected(linkedinCollection.collect(run::collection), run);
        } catch (IOException | UsageLimitExceededException | RuntimeException error) {
            failBatch(run.runId(), error);
            throw error;
        }
    }

    public WorkflowResult processUrls(String... urls) throws IOException, UsageLimitExceededException {
        return processUrls(List.of(urls), ignored -> { });
    }

    public WorkflowResult processUrls(List<String> urls, Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        return processUrls(urls, "", new WorkflowProgress.Run(progress));
    }

    public WorkflowResult checkRecommendations() throws IOException, UsageLimitExceededException {
        return checkRecommendations(ignored -> { });
    }

    public WorkflowResult checkRecommendations(Consumer<ProgressSnapshot> progress)
            throws IOException, UsageLimitExceededException {
        WorkflowProgress.Run run = new WorkflowProgress.Run(progress);
        run.preparing("Collection", "Reading email recommendations");
        List<String> urls = recommendations.load();
        if (urls.isEmpty()) {
            run.complete("No new recommendations");
            return new WorkflowResult("linkedin", null, "complete", null,
                    List.of(), 0, "No new recommendations");
        }
        return processUrls(urls, "recommendations", run);
    }

    private WorkflowResult processUrls(List<String> urls, String runPrefix, WorkflowProgress.Run run)
            throws IOException, UsageLimitExceededException {
        WorkflowResult report;
        try {
            report = processCollected(linkedinUrls.collect(urls, runPrefix, run::collection), run);
        } catch (IOException | UsageLimitExceededException | RuntimeException error) {
            failBatch(run.runId(), error);
            throw error;
        }
        int agentFiltered = report.agentFilteredCount();
        if (agentFiltered > 0) {
            String reason = urls.size() == 1
                    ? "Vacancy rejected by agent relevance filter"
                    : "Vacancies rejected by agent relevance filter: " + agentFiltered;
            return report.withMessage(report.message().isEmpty()
                            ? reason : report.message() + "; " + reason);
        }
        return report;
    }

    public WorkflowResult resume(String runId) throws UsageLimitExceededException {
        return resume(runId, ignored -> { });
    }

    public WorkflowResult resume(String runId, Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        List<ScopeItem> scope = processingGateway.loadCollectedScope(runId);
        try {
            return processScope("linkedin", runId, scope, null, new WorkflowProgress.Run(progress));
        } catch (UsageLimitExceededException | RuntimeException error) {
            failBatch(runId, error);
            throw error;
        }
    }

    private WorkflowResult processCollected(CollectionReport collected, WorkflowProgress.Run run)
            throws UsageLimitExceededException {
        run.collectionResult(collected);
        if (!"complete".equals(collected.status()) || collected.scope().isEmpty()) {
            boolean complete = "complete".equals(collected.status());
            processingGateway.updateBatch(collected.runId(), complete ? "complete" : "failed",
                    "Collection", collected.message());
            if (complete) run.complete(collected.message());
            return new WorkflowResult(collected.source(), collected.runId(), collected.status(),
                    collected, List.of(), 0, collected.message());
        }
        return processScope(collected.source(), collected.runId(), collected.scope(), collected, run);
    }

    private WorkflowResult processScope(String source, String runId, List<ScopeItem> scope,
                                        CollectionReport collected, WorkflowProgress.Run run)
            throws UsageLimitExceededException {
        run.identify(runId);
        run.count("Scope", scope.size());
        run.preparing(collected == null ? "Relevance filter" : "Collection", "Loading processing scope");
        processingGateway.updateBatch(runId, "running", "", "");
        Map<String, String> statuses = new HashMap<>(processingGateway.loadStatuses(source, scope));
        run.count("Already saved", atStatus(scope, statuses, "SAVED").size());
        run.count("Rejected by relevance", atStatus(scope, statuses, "NONRELEVANT").size());
        Set<String> acceptedStatuses = Set.of("RELEVANT", "ANALYZED", "SCORED", "SAVED");
        List<ScopeItem> unfiltered = atStatus(scope, statuses, "CLEANED");
        if (!unfiltered.isEmpty()) {
            batchStage(runId, "Relevance filter");
            run.work("Relevance filter", "Filtering vacancies", 0, unfiltered.size(), null);
            AgentFilterResult result = processingGateway.runRelevanceFilter(runId, unfiltered);
            List<String> nonrelevantIds = validateResult(unfiltered, result.nonrelevantJobIds());
            processingGateway.markRelevance(source, unfiltered, nonrelevantIds);
            Set<String> rejected = Set.copyOf(nonrelevantIds);
            for (ScopeItem item : unfiltered) {
                statuses.put(item.jobId(), rejected.contains(item.jobId()) ? "NONRELEVANT" : "RELEVANT");
            }
            run.count("Rejected by relevance", atStatus(scope, statuses, "NONRELEVANT").size());
            run.count("Relevant", (int) scope.stream()
                    .filter(item -> acceptedStatuses.contains(statuses.get(item.jobId()))).count());
            run.work("Relevance filter", "Filtering complete", unfiltered.size(), unfiltered.size(), null);
        }
        List<ScopeItem> remaining = scope.stream()
                .filter(item -> acceptedStatuses.contains(statuses.get(item.jobId()))).toList();
        List<ScopeItem> facts = atStatus(remaining, statuses, "RELEVANT");
        run.count("Relevant", remaining.size());
        run.total("Job facts", facts.size());
        run.total("Candidate fit", facts.size() + atStatus(remaining, statuses, "ANALYZED").size());
        if (!facts.isEmpty() || !atStatus(remaining, statuses, "ANALYZED").isEmpty()) {
            batchStage(runId, facts.isEmpty() ? "Candidate fit" : "Job facts");
            run.preparing(facts.isEmpty() ? "Candidate fit" : "Job facts", "Preparing analysis");
            processingGateway.initializeAnalysisRun(runId, parallelAgents, vacanciesPerAgent);
        }
        runJobFacts(runId, source, facts, run);
        for (ScopeItem item : facts) statuses.put(item.jobId(), "ANALYZED");
        runFinishAnalysis(runId, source, atStatus(remaining, statuses, "ANALYZED"), run);
        List<ScopeItem> scored = atStatus(remaining, statuses, "SCORED");
        if (!scored.isEmpty()) {
            batchStage(runId, "Saving");
            run.saved(processingGateway.saveScored(source, scored.stream().map(ScopeItem::jobId).toList()));
        }
        processingGateway.updateBatch(runId, "complete", "", collected == null ? "" : collected.message());
        run.complete(collected == null ? "" : collected.message());
        return new WorkflowResult(source, runId, "complete", collected, remaining,
                atStatus(scope, statuses, "NONRELEVANT").size(), run.savedCount(),
                collected == null ? "" : collected.message());
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope) throws UsageLimitExceededException {
        return jobFacts(runId, source, scope, ignored -> { });
    }

    public StageResult jobFacts(String runId, String source, List<ScopeItem> scope,
                                Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        WorkflowProgress.Run run = new WorkflowProgress.Run(progress);
        run.identify(runId);
        run.count("Scope", scope.size());
        run.total("Job facts", scope.size());
        run.preparing("Job facts", "Preparing analysis");
        try {
            batchStage(runId, "Job facts");
            processingGateway.initializeAnalysisRun(runId, parallelAgents, vacanciesPerAgent);
            runJobFacts(runId, source, scope, run);
            processingGateway.updateBatch(runId, "pending", "", "");
            run.complete("");
            return new StageResult(source, runId, scope);
        } catch (UsageLimitExceededException | RuntimeException error) {
            failBatch(runId, error);
            throw error;
        }
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope)
            throws UsageLimitExceededException {
        return finishAnalysis(runId, source, scope, ignored -> { });
    }

    public StageResult finishAnalysis(String runId, String source, List<ScopeItem> scope,
                                      Consumer<ProgressSnapshot> progress) throws UsageLimitExceededException {
        WorkflowProgress.Run run = new WorkflowProgress.Run(progress);
        run.identify(runId);
        run.count("Scope", scope.size());
        run.total("Candidate fit", scope.size());
        run.preparing("Candidate fit", "Preparing analysis");
        try {
            batchStage(runId, "Candidate fit");
            processingGateway.initializeAnalysisRun(runId, parallelAgents, vacanciesPerAgent);
            runFinishAnalysis(runId, source, scope, run);
            processingGateway.updateBatch(runId, "complete", "", "");
            run.complete("");
            return new StageResult(source, runId, scope, run.savedCount());
        } catch (UsageLimitExceededException | RuntimeException error) {
            failBatch(runId, error);
            throw error;
        }
    }

    private void runJobFacts(String runId, String source, List<ScopeItem> scope, WorkflowProgress.Run run)
            throws UsageLimitExceededException {
        if (scope.isEmpty()) return;
        run.work("Job facts", "Extracting facts", 0, scope.size(), null);
        try (var executor = Executors.newFixedThreadPool(parallelAgents)) {
            CompletionService<FactsGroup> results = new ExecutorCompletionService<>(executor);
            int nextGroup = 0;
            for (int worker = 0; worker < parallelAgents && nextGroup < scope.size(); worker++) {
                submitFacts(results, runId, source, scope, newFactsGroup(source, scope.size(), nextGroup));
                nextGroup += Math.min(vacanciesPerAgent, scope.size() - nextGroup);
            }
            for (int completed = 1; completed <= scope.size(); completed++) {
                FactsGroup group = results.take().get();
                run.work("Job facts", "Extracting facts", completed, scope.size(), null);
                if (group.next() < group.end()) {
                    submitFacts(results, runId, source, scope, group);
                } else if (nextGroup < scope.size()) {
                    submitFacts(results, runId, source, scope, newFactsGroup(source, scope.size(), nextGroup));
                    nextGroup += Math.min(vacanciesPerAgent, scope.size() - nextGroup);
                }
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Job facts interrupted", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof UsageLimitExceededException limit) throw limit;
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new IllegalStateException("Job facts failed", cause);
        }
    }

    private record FactsGroup(int next, int end, String target, String threadId) { }

    private FactsGroup newFactsGroup(String source, int total, int start) {
        return new FactsGroup(start, start + Math.min(vacanciesPerAgent, total - start),
                source + ":job_facts:group-" + (start / vacanciesPerAgent + 1), null);
    }

    private void submitFacts(CompletionService<FactsGroup> results, String runId, String source,
                             List<ScopeItem> scope, FactsGroup group) {
        results.submit(() -> {
            ScopeItem item = scope.get(group.next());
            System.err.println("Job facts start: job_id=" + item.jobId());
            long started = System.nanoTime();
            String threadId = processingGateway.extractJobFacts(runId, source, item.jobId(),
                    group.target(), group.threadId());
            System.err.printf("Job facts complete: job_id=%s duration_ms=%d%n",
                    item.jobId(), (System.nanoTime() - started) / 1_000_000);
            return new FactsGroup(group.next() + 1, group.end(), group.target(), threadId);
        });
    }

    private void runFinishAnalysis(String runId, String source, List<ScopeItem> scope, WorkflowProgress.Run run)
            throws UsageLimitExceededException {
        if (scope.isEmpty()) return;
        batchStage(runId, "Candidate fit");
        String threadId = null;
        String target = source + ":candidate_fit";
        int completed = 0;
        for (ScopeItem item : scope) {
            run.work("Candidate fit", "Evaluating fit", completed, scope.size(), item);
            threadId = processingGateway.evaluateCandidateFit(runId, source, item.jobId(), target, threadId);
            run.work("Candidate fit", "Fit evaluated", ++completed, scope.size(), item);
            processingGateway.calculateJobInterest(source, item.jobId());
        }
        batchStage(runId, "Saving");
        run.saved(processingGateway.saveScored(source, scope.stream().map(ScopeItem::jobId).toList()));
    }

    private void batchStage(String runId, String stage) {
        processingGateway.updateBatch(runId, "running", stage, "");
    }

    private void failBatch(String runId, Exception error) {
        if (runId == null) return; // Collection may fail before creating a run.
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        try {
            processingGateway.updateBatch(runId, "failed", "", message);
        } catch (RuntimeException persistenceError) {
            error.addSuppressed(persistenceError);
        }
    }

    private static List<ScopeItem> atStatus(
            List<ScopeItem> scope, Map<String, String> statuses, String status
    ) {
        return scope.stream()
                .filter(item -> status.equals(statuses.get(item.jobId())))
                .toList();
    }

    private static int executionSetting(Path projectRoot, String key) throws IOException {
        Path config = projectRoot.toAbsolutePath().normalize()
                .resolve("Driver/analyzer/config/execution.ini");
        for (String rawLine : Files.readAllLines(config)) {
            String line = rawLine.strip();
            if (line.contains("=") && line.substring(0, line.indexOf('=')).strip().equals(key)) {
                int value = Integer.parseInt(
                        line.substring(line.indexOf('=') + 1).strip()
                );
                if (value > 0) {
                    return value;
                }
            }
        }
        throw new IllegalArgumentException(
                "Missing positive " + key + " in " + config
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
        CollectionReport collect(Consumer<ProgressSnapshot> progress) throws IOException;
    }

    @FunctionalInterface
    interface LinkedInUrls {
        CollectionReport collect(List<String> urls, String runPrefix, Consumer<ProgressSnapshot> progress)
                throws IOException;
    }

    @FunctionalInterface
    interface RecommendationSource {
        List<String> load() throws IOException;
    }
}
