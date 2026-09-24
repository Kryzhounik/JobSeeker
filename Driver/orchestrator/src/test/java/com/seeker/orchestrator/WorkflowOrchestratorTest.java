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
        private int filterInvocations;
        private String runId;
        private List<ScopeItem> scope = List.of();
        private String markedSource;
        private List<String> markedIds = new ArrayList<>();

        private FakeAgentFilter(List<String> rejectedIds) {
            this.rejectedIds = rejectedIds;
        }

        @Override
        public AgentFilterResult filter(String runId, List<ScopeItem> scope) {
            filterInvocations++;
            this.runId = runId;
            this.scope = scope;
            return new AgentFilterResult(rejectedIds);
        }

        @Override
        public void markNonrelevant(String source, List<String> jobIds) {
            markedSource = source;
            markedIds = List.copyOf(jobIds);
        }

        @Override
        public void startAnalysis(String runId, int vacanciesPerAgent) {
        }

        @Override
        public String jobFacts(
                String runId,
                String source,
                String jobId,
                String target,
                String threadId
        ) {
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
            return threadId == null ? target : threadId;
        }

        @Override
        public void jobInterest(String source, String jobId) {
        }

        @Override
        public void saveScored(String source, List<String> jobIds) {
        }
    }
}
