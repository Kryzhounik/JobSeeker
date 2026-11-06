package com.seeker.guifx;

import org.junit.jupiter.api.Test;
import java.util.Comparator;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JobRowsTest {
    private static JobRecord job(int id, int score, boolean duplicate) throws Exception {
        return ClientData.readResponse("{\"job_id\":" + id + ",\"score\":\"" + score + "\",\"title\":\"Job\""
                + (duplicate ? ",\"duplicate_parent\":{\"job_id\":90,\"score\":\"9999\",\"title\":\"Original\"}" : "")
                + "}", JobRecord.class);
    }

    @Test void sortUsesOnlyMatchingJobsAndKeepsEachParentAboveItsChild() throws Exception {
        var low = job(1, 10, true);
        var high = job(2, 80, true);
        var normal = job(3, 50, false);
        Comparator<JobRecord> order = Comparator.comparingInt(row -> Integer.parseInt(row.score()));
        var ascending = JobRows.sorted(List.of(high, normal, low), order);
        assertEquals(List.of(90, 1, 3, 90, 2), ascending.stream().map(JobRecord::jobId).toList());
        assertEquals(1, ascending.getFirst().contextForJobId());
        assertEquals(2, ascending.get(3).contextForJobId());
        var descending = JobRows.sorted(ascending, order.reversed());
        assertEquals(List.of(90, 2, 3, 90, 1), descending.stream().map(JobRecord::jobId).toList());
        assertEquals(descending, JobRows.sorted(descending, order.reversed()));
        assertSame(high, descending.get(1));
        assertEquals("Original", descending.getFirst().title());
    }

    @Test void independentlyMatchingParentStillHasItsOwnSortedPosition() throws Exception {
        var rows = JobRows.sorted(List.of(job(1, 10, true), job(90, 9999, false)),
                Comparator.comparingInt(row -> Integer.parseInt(row.score())));
        assertEquals(List.of(90, 1, 90), rows.stream().map(JobRecord::jobId).toList());
        assertNull(rows.getLast().contextForJobId());
        assertEquals(List.of(), JobRows.sorted(List.of(), null));
    }
}
