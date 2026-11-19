package com.seeker.guifx;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobRowsTest {
    private static JobRecord job(int id, int score, Integer duplicateParentId, int parentScore)
            throws Exception {
        String parent = duplicateParentId == null ? ""
                : ",\"duplicate_parent\":{\"job_id\":" + duplicateParentId
                        + ",\"score\":\"" + parentScore + "\",\"title\":\"Parent "
                        + duplicateParentId + "\"}";
        return ClientData.readResponse("{\"job_id\":" + id + ",\"score\":\"" + score
                + "\",\"title\":\"Job " + id + "\"" + parent + "}", JobRecord.class);
    }

    private static Comparator<JobRecord> byScore() {
        return Comparator.comparingInt(row -> Integer.parseInt(row.score()));
    }

    @Test void filteredChildrenShareContextParentsAndGroupsSortByParent() throws Exception {
        var childA1 = job(11, 90, 100, 40);
        var childB1 = job(21, 80, 200, 70);
        var childA2 = job(12, 85, 100, 40);
        var matchingParent = job(200, 70, null, 0);
        var childB2 = job(22, 75, 200, 70);

        var ascending = JobRows.sorted(
                List.of(childA1, childB1, childA2, matchingParent, childB2), byScore());
        assertEquals(List.of(100, 11, 12, 200, 21, 22), ascending.stream().map(JobRecord::jobId).toList());
        assertSame(matchingParent, ascending.get(3), "a matched parent row must be reused");
        assertEquals(11, ascending.get(0).contextForJobId());
        assertNull(ascending.get(1).contextForJobId());
        assertNull(ascending.get(2).contextForJobId());
        assertNull(ascending.get(4).contextForJobId());
        assertNull(ascending.get(5).contextForJobId());
        assertEquals(List.of(11, 12), ascending.subList(1, 3).stream().map(JobRecord::jobId).toList());
        assertEquals(List.of(21, 22), ascending.subList(4, 6).stream().map(JobRecord::jobId).toList());
        assertEquals(List.of(100, 200), JobRows.parentIds(List.of(childA1, childB1, childA2,
                matchingParent, childB2)).stream().sorted().toList());

        var descending = JobRows.sorted(ascending, byScore().reversed());
        assertEquals(List.of(200, 21, 22, 100, 11, 12), descending.stream().map(JobRecord::jobId).toList());
        assertSame(matchingParent, descending.get(0));
        assertNull(descending.get(1).contextForJobId());
        assertNull(descending.get(2).contextForJobId());
        assertEquals(List.of(21, 22), descending.subList(1, 3).stream().map(JobRecord::jobId).toList());
        assertEquals(List.of(11, 12), descending.subList(4, 6).stream().map(JobRecord::jobId).toList());
        assertSame(ascending.getFirst(), descending.get(3), "context parent should be reused on resort");
        assertSame(ascending.get(3), descending.getFirst(), "matched original parent identity should survive");
        assertEquals(descending, JobRows.sorted(descending, byScore().reversed()));
    }

    @Test void matchedAndContextParentsDoNotDuplicateAndNullComparatorKeepsGroupInputOrder() throws Exception {
        var originalParent = job(90, 9999, null, 0);
        var childA = job(1, 10, 90, 9999);
        var childB = job(2, 20, 90, 9999);
        var otherChild = job(3, 30, 40, 500);

        var rows = JobRows.sorted(List.of(childA, otherChild, originalParent, childB), null);
        assertEquals(List.of(90, 1, 2, 40, 3), rows.stream().map(JobRecord::jobId).toList());
        assertSame(originalParent, rows.getFirst());
        assertNull(rows.getFirst().contextForJobId());
        assertNull(rows.get(1).contextForJobId());
        assertNull(rows.get(2).contextForJobId());
        assertEquals(3, rows.get(3).contextForJobId());
        assertNull(rows.getLast().contextForJobId());
        assertEquals(List.of(), JobRows.sorted(List.of(), null));
        assertEquals(java.util.Set.of(), JobRows.parentIds(List.of()));
    }

    @Test void resortPermutesObservableItemsAndReusesContextParents() throws Exception {
        var childA1 = job(11, 90, 100, 40);
        var childB = job(21, 80, 200, 70);
        var childA2 = job(12, 85, 100, 40);
        var rows = FXCollections.observableArrayList(
                JobRows.sorted(List.of(childA1, childB, childA2), byScore()));
        var contextA = rows.getFirst();
        var contextB = rows.get(3);
        var firstChild = rows.get(1);
        var selectedSibling = rows.get(2);
        var permutationObserved = new boolean[1];
        rows.addListener((ListChangeListener<JobRecord>) change -> {
            while (change.next()) if (change.wasPermutated()) permutationObserved[0] = true;
        });

        JobRows.sort(rows, byScore().reversed());

        assertTrue(permutationObserved[0], "sort should expose an observable permutation");
        assertEquals(List.of(200, 21, 100, 11, 12), rows.stream().map(JobRecord::jobId).toList());
        assertSame(contextB, rows.getFirst());
        assertSame(childB, rows.get(1));
        assertSame(contextA, rows.get(2));
        assertSame(firstChild, rows.get(3));
        assertSame(selectedSibling, rows.getLast());
        assertSame(selectedSibling, rows.stream().filter(row -> row.jobId() == 12).findFirst().orElseThrow());
    }
}
