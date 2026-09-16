package com.seeker.orchestrator;

import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.collection.ScopeItem;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowOrchestratorTest {
    @Test
    void delegatesLinkedInBatchAndReturnsCollectorReportUnchanged() throws Exception {
        CollectionReport expected = new CollectionReport(
                "linkedin",
                "run-1",
                "complete",
                1,
                List.of(new ScopeItem("linkedin", "123", "Java Developer")),
                Map.of("raw_saved", 1),
                List.of(),
                ""
        );
        AtomicInteger invocations = new AtomicInteger();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(() -> {
            invocations.incrementAndGet();
            return expected;
        });

        CollectionReport actual = orchestrator.batch("linkedin");

        assertSame(expected, actual);
        assertEquals(1, invocations.get());
    }

    @Test
    void rejectsUnsupportedBatchSourceBeforeCallingCollector() {
        AtomicInteger invocations = new AtomicInteger();
        WorkflowOrchestrator orchestrator = new WorkflowOrchestrator(() -> {
            invocations.incrementAndGet();
            throw new AssertionError("collector must not be called");
        });

        assertThrows(
                IllegalArgumentException.class,
                () -> orchestrator.batch("unknown")
        );
        assertEquals(0, invocations.get());
    }
}
