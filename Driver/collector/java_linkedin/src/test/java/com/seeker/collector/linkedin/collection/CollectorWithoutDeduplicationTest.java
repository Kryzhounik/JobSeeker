package com.seeker.collector.linkedin.collection;
import com.seeker.collector.common.*;
import com.seeker.collector.core.CollectionAccumulator;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CollectorWithoutDeduplicationTest {
    @Test void acceptedScopeKeepsOrderAndSeparatePublications() {
        var sink = new CollectionAccumulator();
        sink.record(new ProcessResult("linkedin","20","Java Engineer","raw_saved",""));
        sink.record(new ProcessResult("linkedin","99","Java Engineer","content_filtered","rejected"));
        sink.record(new ProcessResult("linkedin","3","Java Engineer","raw_saved",""));
        var report = sink.report("linkedin", "run", new SourceResult("complete", "done", ""), List.of());
        assertEquals(2, report.acceptedCount());
        assertEquals(List.of("20","3"), report.scope().stream().map(ScopeItem::jobId).toList());
        assertEquals(Map.of("raw_saved",2,"content_filtered",1), report.outcomes());
        assertEquals("complete", report.status());
    }
}
