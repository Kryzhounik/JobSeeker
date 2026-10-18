package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
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
        FakeAgentFilter filter = new FakeAgentFilter(List.of("2"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> collected,
                filter
        );

        CollectionReport actual = orchestrator.batch("linkedin");

        assertEquals("run-1", filter.runId);
        assertEquals(collected.scope(), filter.scope);
        assertEquals("linkedin", filter.markedSource);
        assertEquals(List.of("2"), filter.markedIds);
        assertEquals(List.of("1", "3"), ids(actual.scope()));
        assertEquals(2, actual.acceptedCount());
        assertEquals(1, actual.outcomes().get("agent_filtered"));
        assertEquals("", actual.message());
    }

    @Test
    void checksUrlAndRunsAllExistingAnalysisStagesForAcceptedVacancy()
            throws Exception {
        String url = "https://www.linkedin.com/jobs/view/1";
        List<String> requestedUrls = new ArrayList<>();
        FakeAgentFilter filter = new FakeAgentFilter(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> {
                    throw new AssertionError("batch collector must not be called");
                },
                requestedUrl -> {
                    requestedUrls.add(requestedUrl);
                    return report("complete", List.of(item("1", "Java Developer")));
                },
                filter
        );

        CollectionReport actual = orchestrator.checkUrl(url);

        assertEquals(List.of(url), requestedUrls);
        assertEquals(List.of("1"), ids(actual.scope()));
        assertEquals(1, actual.acceptedCount());
        assertEquals(List.of(
                "filter:run-1",
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
        FakeAgentFilter filter = new FakeAgentFilter(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> rejected,
                filter
        );

        CollectionReport actual = orchestrator.checkUrl(
                "https://www.linkedin.com/jobs/view/1"
        );

        assertSame(rejected, actual);
        assertEquals(List.of(), filter.calls);
    }

    @Test
    void explainsAgentRejectionWithoutStartingLaterAnalysis() throws Exception {
        FakeAgentFilter filter = new FakeAgentFilter(List.of("1"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> report("complete", List.of(item("1", "Java Developer"))),
                filter
        );

        CollectionReport actual = orchestrator.checkUrl(
                "https://www.linkedin.com/jobs/view/1"
        );

        assertEquals("complete", actual.status());
        assertEquals(0, actual.acceptedCount());
        assertEquals(List.of(), actual.scope());
        assertEquals(1, actual.outcomes().get("agent_filtered"));
        assertEquals("Vacancy rejected by agent relevance filter", actual.message());
        assertEquals(List.of("filter:run-1", "mark-nonrelevant:1"), filter.calls);
    }

    @Test
    void rejectsFilterIdsOutsideCollectedScopeBeforeUpdatingStatuses()
            throws Exception {
        FakeAgentFilter filter = new FakeAgentFilter(List.of("outside"));
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> report("complete", List.of(item("1", "Java Developer"))),
                filter
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.batch("linkedin")
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
        FakeAgentFilter filter = new FakeAgentFilter(List.of());
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> blocked,
                filter
        );

        CollectionReport actual = orchestrator.batch("linkedin");

        assertSame(blocked, actual);
        assertEquals(0, filter.filterInvocations);
    }

    @Test
    void rejectsUnsupportedBatchSourceBeforeCallingCollector() {
        AtomicInteger invocations = new AtomicInteger();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(
                () -> {
                    invocations.incrementAndGet();
                    throw new AssertionError("collector must not be called");
                },
                new FakeAgentFilter(List.of())
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.batch("unknown")
        );
        assertEquals(0, invocations.get());
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

    private static final class FakeAgentFilter implements AgentFilterGateway {
        private final List<String> rejectedIds;
        private final List<String> calls = new ArrayList<>();
        private int filterInvocations;
        private String runId;
        private List<ScopeItem> scope = List.of();
        private String markedSource;
        private List<String> markedIds = new ArrayList<>();

        private FakeAgentFilter(List<String> rejectedIds) {
            this.rejectedIds = rejectedIds;
        }

        @Override
        public List<ScopeItem> collectedScope(String runId) {
            return List.of();
        }

        @Override
        public AgentFilterResult filter(String runId, List<ScopeItem> scope) {
            calls.add("filter:" + runId);
            filterInvocations++;
            this.runId = runId;
            this.scope = scope;
            return new AgentFilterResult(rejectedIds);
        }

        @Override
        public void markNonrelevant(String source, List<String> jobIds) {
            calls.add("mark-nonrelevant:" + String.join(",", jobIds));
            markedSource = source;
            markedIds = List.copyOf(jobIds);
        }

        @Override
        public void startAnalysis(String runId, int vacanciesPerAgent) {
            calls.add("start-analysis:" + runId);
        }

        @Override
        public String jobFacts(
                String runId,
                String source,
                String jobId,
                String target,
                String threadId
        ) {
            calls.add("job-facts:" + jobId);
            return threadId == null ? target : threadId;
        }

        @Override
        public String candidateFit(
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
        public void jobInterest(String source, String jobId) {
            calls.add("job-interest:" + jobId);
        }

        @Override
        public void saveScored(String source, List<String> jobIds) {
            calls.add("save-scored:" + String.join(",", jobIds));
        }
    }
}
