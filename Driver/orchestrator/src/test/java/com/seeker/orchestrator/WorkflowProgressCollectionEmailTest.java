package com.seeker.orchestrator;

import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowProgressCollectionEmailTest {
    @Test void recommendationSnapshotPassesThroughBeforeCollectionProgress() {
        var received = new ArrayList<ProgressSnapshot>();
        var run = new WorkflowProgress.Run(received::add);
        var email = new ImmutableProgressSnapshot("batch-run", "Email recommendations", "Reading emails",
                "Email recommendations", Map.of("Checked emails", "2"), "");
        var collection = new ImmutableProgressSnapshot("batch-run", "Collection", "complete",
                "Collection 3", Map.of("Accepted total", "3"), "");

        run.collection(email);

        assertEquals(1, received.size());
        assertSame(email, received.getFirst());
        assertEquals("batch-run", run.runId());

        run.collection(collection);

        assertEquals(2, received.size());
        assertEquals("batch-run", received.getLast().runId());
        assertEquals("Collection", received.getLast().stage());
        assertEquals("Collection 3", received.getLast().progress());
    }
}
