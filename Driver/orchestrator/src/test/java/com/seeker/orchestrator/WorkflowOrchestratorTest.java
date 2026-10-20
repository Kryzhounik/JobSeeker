package com.seeker.orchestrator;

import com.google.gson.Gson;
import com.google.gson.JsonNull;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.PageReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowOrchestratorTest {
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
                () -> collected,
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
                () -> {
                    throw new AssertionError("collector must not be called");
                },
                urls -> {
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
                () -> rejected,
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
                () -> {
                    throw new AssertionError("collector must not be called");
                },
                requested -> {
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
                () -> collected,
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
                () -> report("complete", List.of(item("1", "Java Developer"))),
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
                () -> report("complete", List.of(item("1", "Java Developer"))),
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
                () -> blocked,
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
                () -> {
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
    void resumesMixedStagesAndDoesNoWorkOnCompletedRun() {
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
                () -> { throw new AssertionError("collector must not be called"); }, filter
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
        assertNull(result.collection());
        assertEquals(JsonNull.INSTANCE, Main.GSON.toJsonTree(result).getAsJsonObject().get("collection"));
        int completedCalls = filter.calls.size();

        assertEquals(result.processedScope(), orchestrator.resume("run-1").processedScope());
        assertEquals(completedCalls, filter.calls.size());
    }

    @Test
    void serializesWorkflowContractWithoutChangingCollectorReport() {
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
    void partialMethodsReturnTheirJsonContract() {
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> { throw new AssertionError("collector must not be called"); },
                new FakeProcessingGateway(List.of())
        );
        List<ScopeItem> scope = List.of(item("1", "Java"));
        Gson gson = Main.GSON;
        var expected = gson.fromJson("""
                {"source":"linkedin","run_id":"run-1","status":"complete",
                 "processed_count":1,"message":"",
                 "processed_scope":[{"source":"linkedin","job_id":"1","title":"Java"}]}
                """, com.google.gson.JsonObject.class);

        assertEquals(expected, gson.toJsonTree(orchestrator.jobFacts("run-1", "linkedin", scope)));
        assertEquals(expected, gson.toJsonTree(orchestrator.finishAnalysis("run-1", "linkedin", scope)));
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
        public AgentFilterResult runRelevanceFilter(String runId, List<ScopeItem> scope) {
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
        ) {
            calls.add("job-facts:" + jobId);
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
        ) {
            calls.add("candidate-fit:" + jobId);
            return threadId == null ? target : threadId;
        }

        @Override
        public void calculateJobInterest(String source, String jobId) {
            calls.add("job-interest:" + jobId);
            states.put(jobId, "SCORED");
        }

        @Override
        public void saveScored(String source, List<String> jobIds) {
            calls.add("save-scored:" + String.join(",", jobIds));
            jobIds.forEach(jobId -> states.put(jobId, "SAVED"));
        }
    }
}
