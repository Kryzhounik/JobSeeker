package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.PageReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowOrchestratorTest {
    @Test
    void reportsWorkflowStagesCountsAndCurrentJobs() throws Exception {
        List<ProgressSnapshot> updates = new ArrayList<>();
        FakeProcessingGateway gateway = new FakeProcessingGateway(List.of("2"));
        WorkflowOrchestrator workflow = new WorkflowOrchestrator(
                progress -> report("complete", List.of(item("1", "Java"), item("2", "Marketing"),
                        item("3", "Backend"))), gateway);

        WorkflowResult result = workflow.processBatch("linkedin", updates::add);

        assertEquals(List.of("Collection", "Relevance filter", "Job facts", "Candidate fit"),
                updates.stream().map(ProgressSnapshot::stage).distinct().toList());
        assertTrue(updates.stream().allMatch(update -> update.runId().equals("run-1")));
        Map<String, List<String>> stageFields = Map.of(
                "Collection", new ArrayList<>(updates.getFirst().fields().keySet()),
                "Relevance filter", List.of("Scope", "Relevance filter", "Relevant", "Rejected by relevance"),
                "Job facts", List.of("Job facts", "Current vacancy"),
                "Candidate fit", List.of("Candidate fit", "Current vacancy"));
        for (ProgressSnapshot update : updates) {
            assertFalse(update.fields().containsKey("Run ID"));
            if (!update.action().equals("Complete")) {
                assertEquals(stageFields.get(update.stage()), new ArrayList<>(update.fields().keySet()));
            }
        }
        assertFalse(updates.getFirst().fields().containsKey("Job facts"));
        assertFalse(updates.getLast().fields().containsKey("Location"));
        assertEquals("1", updates.getLast().fields().get("Rejected by relevance"));
        assertEquals(List.of("1", "3"), updates.stream()
                .filter(update -> update.action().equals("Evaluating fit"))
                .map(update -> update.fields().get("Current vacancy")).toList());
        assertEquals("2 / 2", updates.getLast().fields().get("Job facts"));
        assertEquals("2 / 2", updates.getLast().fields().get("Candidate fit"));
        assertEquals("2", updates.getLast().fields().get("Saved"));
        assertEquals(2, result.savedCount());
    }

    @Test
    void analysisFailureLeavesLastStageWithoutReportingCompletion() {
        List<ProgressSnapshot> updates = new ArrayList<>();
        FakeProcessingGateway gateway = new FakeProcessingGateway(List.of());
        gateway.factsFailure = new IllegalStateException("analysis failed");
        WorkflowOrchestrator workflow = new WorkflowOrchestrator(
                progress -> report("complete", List.of(item("1", "Java"))), gateway);

        assertEquals(gateway.factsFailure, assertThrows(IllegalStateException.class,
                () -> workflow.processBatch("linkedin", updates::add)));
        assertEquals("Job facts", updates.getLast().stage());
        assertEquals("1", updates.getLast().fields().get("Current vacancy"));
        assertEquals("run-1", updates.getLast().runId());
        assertEquals("0 / 1", updates.getLast().fields().get("Job facts"));
        assertFalse(updates.stream().anyMatch(update -> update.action().equals("Complete")));
    }

    @Test
    void filtersCompleteScopeMarksRejectedJobsAndPreservesRemainingOrder()
            throws Exception {
        CollectionReport collected = report(
                "complete",
                List.of(
                        item("1", "Java Developer"),
                        item("2", "Marketing Manager"),
                        item("3", "Backend Engineer")
                )
        );
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of("2"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> collected,
                filter
        );

        WorkflowResult actual = orchestrator.processBatch("linkedin");

        assertEquals("run-1", filter.runId);
        assertEquals(collected.scope(), filter.scope);
        assertEquals("linkedin", filter.markedSource);
        assertEquals(List.of("2"), filter.markedIds);
        assertEquals(List.of("1", "3"), ids(actual.processedScope()));
        assertEquals(2, actual.processedCount());
        assertEquals(1, actual.agentFilteredCount());
        assertEquals(collected.runId(), actual.collection().runId());
        assertEquals(3, actual.collection().acceptedCount());
        assertEquals(List.of("1", "2", "3"), ids(actual.collection().scope()));
        assertEquals(Map.of("raw_saved", 3), actual.collection().outcomes());
        assertEquals("", actual.message());
    }

    @Test
    void processesUrlAndRunsAllExistingAnalysisStagesForAcceptedVacancy()
            throws Exception {
        String url = "https://www.linkedin.com/jobs/view/1";
        List<String> requestedUrls = new ArrayList<>();
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> {
                    throw new AssertionError("collector must not be called");
                },
                (urls, prefix, progress) -> {
                    assertEquals("", prefix);
                    requestedUrls.addAll(urls);
                    return report("complete", List.of(item("1", "Java Developer")));
                },
                filter
        );

        WorkflowResult actual = orchestrator.processUrls(url);

        assertEquals(List.of(url), requestedUrls);
        assertEquals(List.of("1"), ids(actual.processedScope()));
        assertEquals(1, actual.processedCount());
        assertEquals(List.of(
                "filter:run-1",
                "mark-relevance:",
                "start-analysis:run-1",
                "job-facts:1",
                "candidate-fit:1",
                "job-interest:1",
                "save-scored:1"
        ), filter.calls);
    }

    @Test
    void returnsCollectorRejectionWithoutStartingAnalysis() throws Exception {
        CollectionReport rejected = new CollectionReport(
                "linkedin", "run-1", "complete", 0, List.of(),
                Map.of("preview_filtered", 1), List.of(), "duplicate source_job_id"
        );
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> rejected,
                filter
        );

        WorkflowResult actual = orchestrator.processUrls(
                "https://www.linkedin.com/jobs/view/1"
        );

        assertEquals(rejected.message(), actual.collection().message());
        assertEquals(0, actual.processedCount());
        assertEquals(List.of(), filter.calls);
    }

    @Test
    void processesMultipleUrlsAsOneScopeAndRunsAnalysisOnce() throws Exception {
        List<String> urls = List.of(
                "https://www.linkedin.com/jobs/view/1",
                "https://www.linkedin.com/jobs/view/2"
        );
        List<String> requestedUrls = new ArrayList<>();
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> {
                    throw new AssertionError("collector must not be called");
                },
                (requested, prefix, progress) -> {
                    requestedUrls.addAll(requested);
                    return report("complete", List.of(
                            item("1", "Java Developer"),
                            item("2", "Backend Engineer")
                    ));
                },
                filter
        );

        WorkflowResult actual = orchestrator.processUrls(urls.toArray(String[]::new));

        assertEquals(urls, requestedUrls);
        assertEquals(List.of("1", "2"), ids(actual.processedScope()));
        assertEquals(2, actual.processedCount());
        assertEquals(List.of(
                "filter:run-1",
                "mark-relevance:",
                "start-analysis:run-1",
                "job-facts:1",
                "job-facts:2",
                "candidate-fit:1",
                "job-interest:1",
                "candidate-fit:2",
                "job-interest:2",
                "save-scored:1,2"
        ), filter.calls);
    }

    @Test
    void keepsCollectorAndAgentRejectionReasonsForMultipleUrls() throws Exception {
        CollectionReport collected = new CollectionReport(
                "linkedin", "run-1", "complete", 1,
                List.of(item("1", "Java Developer")),
                Map.of("raw_saved", 1, "preview_filtered", 1),
                List.of(), "2: duplicate source_job_id"
        );
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of("1"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> collected,
                filter
        );

        WorkflowResult actual = orchestrator.processUrls(
                "https://www.linkedin.com/jobs/view/1",
                "https://www.linkedin.com/jobs/view/2"
        );

        assertEquals(List.of(), actual.processedScope());
        assertEquals(0, actual.processedCount());
        assertEquals(1, actual.collection().outcomes().get("preview_filtered"));
        assertEquals(1, actual.agentFilteredCount());
        assertEquals(1, actual.collection().acceptedCount());
        assertEquals(
                "2: duplicate source_job_id; Vacancies rejected by agent relevance filter: 1",
                actual.message()
        );
        assertEquals(List.of("filter:run-1", "mark-relevance:1"), filter.calls);
    }

    @Test
    void explainsAgentRejectionWithoutStartingLaterAnalysis() throws Exception {
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of("1"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> report("complete", List.of(item("1", "Java Developer"))),
                filter
        );

        WorkflowResult actual = orchestrator.processUrls(
                "https://www.linkedin.com/jobs/view/1"
        );

        assertEquals("complete", actual.status());
        assertEquals(0, actual.processedCount());
        assertEquals(List.of(), actual.processedScope());
        assertEquals(1, actual.agentFilteredCount());
        assertEquals("Vacancy rejected by agent relevance filter", actual.message());
        assertEquals(List.of("filter:run-1", "mark-relevance:1"), filter.calls);
    }

    @Test
    void rejectsFilterIdsOutsideCollectedScopeBeforeUpdatingStatuses()
            throws Exception {
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of("outside"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> report("complete", List.of(item("1", "Java Developer"))),
                filter
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.processBatch("linkedin")
        );

        assertEquals(
                "Agent filter returned job ID outside scope: outside",
                error.getMessage()
        );
        assertEquals(List.of(), filter.markedIds);
    }

    @Test
    void skipsAgentFilterWhenCollectionDidNotComplete() throws Exception {
        CollectionReport blocked = report(
                "blocked",
                List.of(item("1", "Java Developer"))
        );
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> blocked,
                filter
        );

        WorkflowResult actual = orchestrator.processBatch("linkedin");

        assertEquals(blocked.status(), actual.collection().status());
        assertEquals("blocked", actual.status());
        assertEquals(0, actual.processedCount());
        assertEquals(0, filter.filterInvocations);
    }

    @Test
    void rejectsUnsupportedProcessSourceBeforeCallingCollector() {
        AtomicInteger invocations = new AtomicInteger();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> {
                    invocations.incrementAndGet();
                    throw new AssertionError("collector must not be called");
                },
                new FakeProcessingGateway(List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.processBatch("unknown")
        );
        assertEquals(0, invocations.get());
    }

    @Test
    void resumesMixedStagesAndDoesNoWorkOnCompletedRun() throws Exception {
        FakeProcessingGateway filter = new FakeProcessingGateway(List.of());
        filter.recoveredScope = List.of(
                item("1", "Java"), item("2", "Java"), item("3", "Java"),
                item("4", "Java"), item("5", "Java"), item("6", "Marketing")
        );
        filter.states.putAll(Map.of(
                "1", "CLEANED", "2", "RELEVANT", "3", "ANALYZED",
                "4", "SCORED", "5", "SAVED", "6", "NONRELEVANT"
        ));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> { throw new AssertionError("collector must not be called"); }, filter
        );

        WorkflowResult result = orchestrator.resume("run-1");

        assertEquals(List.of("1"), ids(filter.scope));
        assertEquals(List.of(
                "filter:run-1", "mark-relevance:", "start-analysis:run-1",
                "job-facts:1", "job-facts:2",
                "candidate-fit:1", "job-interest:1",
                "candidate-fit:2", "job-interest:2",
                "candidate-fit:3", "job-interest:3",
                "save-scored:1,2,3", "save-scored:4"
        ), filter.calls);
        assertEquals(List.of("1", "2", "3", "4", "5"), ids(result.processedScope()));
        assertEquals(1, result.agentFilteredCount());
        assertEquals(4, result.savedCount());
        assertNull(result.collection());
        assertEquals(JsonNull.INSTANCE, Main.GSON.toJsonTree(result).getAsJsonObject().get("collection"));
        int completedCalls = filter.calls.size();

        WorkflowResult resumed = orchestrator.resume("run-1");
        assertEquals(result.processedScope(), resumed.processedScope());
        assertEquals(0, resumed.savedCount());
        assertEquals(completedCalls, filter.calls.size());
    }

    @Test
    void serializesWorkflowContractWithoutChangingCollectorReport() throws Exception {
        Gson gson = Main.GSON;
        CollectionReport collected = new CollectionReport(
                "linkedin", "run-1", "complete", 2,
                List.of(item("1", "Java"), item("2", "Marketing")),
                Map.of("raw_saved", 2),
                List.of(gson.fromJson("{\"label\":\"Ukraine\"}", PageReport.class)), ""
        );
        WorkflowResult result = new WorkflowResult(
                "linkedin", "run-1", "complete", collected,
                List.of(item("1", "Java")), 1, ""
        );

        var json = gson.toJsonTree(result).getAsJsonObject();

        assertEquals(1, result.processedCount());
        assertEquals(1, json.get("processed_count").getAsInt());
        assertEquals(2, json.getAsJsonObject("collection").get("accepted_count").getAsInt());
        assertFalse(json.getAsJsonObject("collection").has("pages"));
        assertEquals(1, collected.pages().size());
        assertEquals("Ukraine", collected.pages().getFirst().label());
        assertEquals(1, gson.toJsonTree(collected).getAsJsonObject().getAsJsonArray("pages").size());
    }

    @Test
    void recommendationsUseTheExistingWorkflowWithAPrefix() throws Exception {
        List<String> urls = List.of("https://www.linkedin.com/jobs/view/1");
        FakeProcessingGateway processing = new FakeProcessingGateway(List.of());
        AtomicInteger collections = new AtomicInteger();
        AtomicInteger mailCalls = new AtomicInteger();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> { throw new AssertionError("search must not be called"); },
                (requested, prefix, progress) -> {
                    collections.incrementAndGet();
                    assertEquals(urls, requested);
                    assertEquals("recommendations", prefix);
                    return report("complete", List.of(item("1", "Java")));
                }, processing,
                () -> { mailCalls.incrementAndGet(); return urls; }
        );

        WorkflowResult result = orchestrator.checkRecommendations();

        assertEquals(1, collections.get());
        assertEquals(1, mailCalls.get());
        assertEquals(1, result.processedCount());
        assertEquals("save-scored:1", processing.calls.getLast());
    }

    @Test
    void emptyRecommendationsAndMailErrorsDoNotStartCollection() throws Exception {
        WorkflowOrchestrator.LinkedInCollection search =
                progress -> { throw new AssertionError("search must not be called"); };
        WorkflowOrchestrator.LinkedInUrls urls =
                (requested, prefix, progress) -> { throw new AssertionError("URLs must not be collected"); };
        WorkflowOrchestrator empty = new WorkflowOrchestrator(
                search, urls, new FakeProcessingGateway(List.of()), List::of);

        WorkflowResult result = empty.checkRecommendations();
        assertEquals("complete", result.status());
        assertEquals(0, result.processedCount());
        assertNull(result.runId());
        assertNull(result.collection());

        IOException failure = new IOException("mail failed");
        WorkflowOrchestrator failed = new WorkflowOrchestrator(
                search, urls, new FakeProcessingGateway(List.of()), () -> { throw failure; });
        assertEquals(failure, assertThrows(IOException.class, failed::checkRecommendations));
    }

    @Test
    void partialMethodsReturnTheirJsonContract() throws Exception {
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                progress -> { throw new AssertionError("collector must not be called"); },
                new FakeProcessingGateway(List.of())
        );
        List<ScopeItem> scope = List.of(item("1", "Java"));
        Gson gson = Main.GSON;
        var expected = gson.fromJson("""
                {"source":"linkedin","run_id":"run-1","status":"complete",
                 "processed_count":1,"saved_count":0,"message":"",
                 "processed_scope":[{"source":"linkedin","job_id":"1","title":"Java"}]}
                """, com.google.gson.JsonObject.class);

        assertEquals(expected, gson.toJsonTree(orchestrator.jobFacts("run-1", "linkedin", scope)));
        expected.addProperty("saved_count", 1);
        assertEquals(expected, gson.toJsonTree(orchestrator.finishAnalysis("run-1", "linkedin", scope)));
    }

    @Test
    void reusedOrchestratorKeepsCollectionAndWorkflowHandlersLocalToEachCall() throws Exception {
        AtomicInteger runs = new AtomicInteger();
        WorkflowOrchestrator workflow = new WorkflowOrchestrator(progress -> {
            String runId = "run-" + runs.incrementAndGet();
            progress.accept(new CollectionProgress(runId, "processing", "Poland", "priority", 1, 25,
                    1, 100, Map.of("raw_saved", 1, "age_filtered", 2), "collecting"));
            return new CollectionReport("linkedin", runId, "complete", 1,
                    List.of(item("1", "Java")), Map.of("raw_saved", 1), List.of(), "");
        }, new FakeProcessingGateway(List.of()));
        List<ProgressSnapshot> first = new ArrayList<>();
        List<ProgressSnapshot> second = new ArrayList<>();

        workflow.processBatch("linkedin", first::add);
        int firstCount = first.size();
        workflow.processBatch("linkedin", second::add);
        int secondCount = second.size();
        workflow.processBatch("linkedin");

        assertEquals(firstCount, first.size());
        assertEquals(secondCount, second.size());
        assertTrue(first.stream().allMatch(update -> update.runId().equals("run-1")));
        assertTrue(second.stream().allMatch(update -> update.runId().equals("run-2")));
        assertEquals("Collection", first.getFirst().stage());
        assertEquals("1 / 100", first.getFirst().fields().get("Accepted / limit"));
        assertFalse(first.getFirst().fields().containsKey("Saved"));
        assertEquals("1", first.getLast().fields().get("Saved"));
        assertEquals("Poland", first.getFirst().fields().get("Location"));
        assertEquals("Priority companies", first.getFirst().fields().get("Search"));
        assertEquals("25", first.getFirst().fields().get("Cards on page"));
        assertEquals("2", first.getFirst().fields().get("Rejected by age"));
        assertFalse(first.getLast().fields().containsKey("Cards on page"));
        assertEquals("Complete", second.getLast().action());
        assertEquals("1", second.getLast().fields().get("Already saved"));
        assertEquals("0", second.getLast().fields().get("Saved"));
    }

    @Test
    void propagatesUsageLimitUnchangedAndAllowsTheNextCall() throws Exception {
        for (String stage : List.of("filter", "job_facts", "candidate_fit")) {
            FakeProcessingGateway gateway = new FakeProcessingGateway(List.of());
            UsageLimitExceededException limit = new UsageLimitExceededException(
                    "Quota exhausted", Instant.parse("2026-10-22T12:20:00Z"));
            gateway.usageLimit = limit;
            gateway.limitStage = stage;
            WorkflowOrchestrator workflow = new WorkflowOrchestrator(
                    progress -> report("complete", List.of(item("1", "Java"))), gateway);
            List<ProgressSnapshot> updates = new ArrayList<>();

            assertSame(limit, assertThrows(UsageLimitExceededException.class,
                    () -> workflow.processBatch("linkedin", updates::add)));
            assertFalse(updates.stream().anyMatch(update -> "Complete".equals(update.action())));
            String lastAction = switch (stage) {
                case "filter" -> "Filtering vacancies";
                case "job_facts" -> "Extracting facts";
                case "candidate_fit" -> "Evaluating fit";
                default -> throw new AssertionError(stage);
            };
            assertEquals(lastAction, updates.getLast().action());
            assertEquals(stage.equals("filter") ? "" : "Java", updates.getLast().details());
            assertEquals(stage.equals("filter") ? null : "1", updates.getLast().fields().get("Current vacancy"));
            int previousCount = updates.size();
            gateway.usageLimit = null;
            assertEquals("complete", workflow.processBatch("linkedin").status());
            assertEquals(previousCount, updates.size());
        }
    }

    @Test
    void savedStatisticsUseConfirmedWritesRatherThanScopeSize() throws Exception {
        FakeProcessingGateway gateway = new FakeProcessingGateway(List.of());
        gateway.confirmedSaved = 1;
        WorkflowOrchestrator workflow = new WorkflowOrchestrator(
                progress -> report("complete", List.of(item("1", "Java"), item("2", "Java"))), gateway);
        List<ProgressSnapshot> updates = new ArrayList<>();

        WorkflowResult result = workflow.processBatch("linkedin", updates::add);

        assertEquals(2, result.processedCount());
        assertEquals(1, result.savedCount());
        assertEquals("1", updates.getLast().fields().get("Saved"));
        assertEquals("Complete", updates.getLast().action());
    }

    private static CollectionReport report(String status, List<ScopeItem> scope) {
        return new CollectionReport(
                "linkedin",
                "run-1",
                status,
                scope.size(),
                scope,
                Map.of("raw_saved", scope.size()),
                List.of(),
                ""
        );
    }

    private static ScopeItem item(String id, String title) {
        return new ScopeItem("linkedin", id, title);
    }

    private static List<String> ids(List<ScopeItem> scope) {
        return scope.stream().map(ScopeItem::jobId).toList();
    }

    private static final class FakeProcessingGateway implements ProcessingGateway {
        private final List<String> rejectedIds;
        private final List<String> calls = new ArrayList<>();
        private final Map<String, String> states = new HashMap<>();
        private List<ScopeItem> recoveredScope = List.of();
        private int filterInvocations;
        private String runId;
        private List<ScopeItem> scope = List.of();
        private String markedSource;
        private List<String> markedIds = new ArrayList<>();
        private RuntimeException factsFailure;
        private UsageLimitExceededException usageLimit;
        private String limitStage;
        private Integer confirmedSaved;

        private FakeProcessingGateway(List<String> rejectedIds) {
            this.rejectedIds = rejectedIds;
        }

        @Override
        public List<ScopeItem> loadCollectedScope(String runId) {
            return recoveredScope;
        }

        @Override
        public Map<String, String> loadStatuses(String source, List<ScopeItem> scope) {
            for (ScopeItem item : scope) {
                states.putIfAbsent(item.jobId(), "CLEANED");
            }
            return states;
        }

        @Override
        public AgentFilterResult runRelevanceFilter(String runId, List<ScopeItem> scope)
                throws UsageLimitExceededException {
            if (usageLimit != null && "filter".equals(limitStage)) throw usageLimit;
            calls.add("filter:" + runId);
            filterInvocations++;
            this.runId = runId;
            this.scope = scope;
            return new AgentFilterResult(rejectedIds);
        }

        @Override
        public void markRelevance(
                String source, List<ScopeItem> scope, List<String> jobIds
        ) {
            calls.add("mark-relevance:" + String.join(",", jobIds));
            markedSource = source;
            markedIds = List.copyOf(jobIds);
            for (ScopeItem item : scope) {
                states.put(item.jobId(), jobIds.contains(item.jobId())
                        ? "NONRELEVANT" : "RELEVANT");
            }
        }

        @Override
        public void initializeAnalysisRun(String runId, int vacanciesPerAgent) {
            calls.add("start-analysis:" + runId);
        }

        @Override
        public String extractJobFacts(
                String runId,
                String source,
                String jobId,
                String target,
                String threadId
        ) throws UsageLimitExceededException {
            if (usageLimit != null && "job_facts".equals(limitStage)) throw usageLimit;
            calls.add("job-facts:" + jobId);
            if (factsFailure != null) throw factsFailure;
            states.put(jobId, "ANALYZED");
            return threadId == null ? target : threadId;
        }

        @Override
        public String evaluateCandidateFit(
                String runId,
                String source,
                String jobId,
                String target,
                String threadId
        ) throws UsageLimitExceededException {
            if (usageLimit != null && "candidate_fit".equals(limitStage)) throw usageLimit;
            calls.add("candidate-fit:" + jobId);
            return threadId == null ? target : threadId;
        }

        @Override
        public void calculateJobInterest(String source, String jobId) {
            calls.add("job-interest:" + jobId);
            states.put(jobId, "SCORED");
        }

        @Override
        public int saveScored(String source, List<String> jobIds) {
            calls.add("save-scored:" + String.join(",", jobIds));
            jobIds.forEach(jobId -> states.put(jobId, "SAVED"));
            return confirmedSaved == null ? jobIds.size() : confirmedSaved;
        }
    }
}
