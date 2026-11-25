package com.seeker.easyreasoning;

import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SemanticDuplicateProgressTest {
    private static VacancySnapshot vacancy(long id, String jobId, String text, boolean checked, Long duplicateOf) {
        return new VacancySnapshot(id, "source", jobId, 1L, "Engineer", text, checked, duplicateOf);
    }

    @Test
    void reportsInitialEachCompletedPairAndFinalSnapshots() {
        List<ProgressSnapshot> updates = run(List.of(
                vacancy(1, "A", "A", false, null), vacancy(2, "B", "B", false, null),
                vacancy(3, "C", "C", false, null)), (a, b) -> false);

        assertEquals(List.of("0 / 3", "1 / 3", "2 / 3", "3 / 3", "3 / 3"), comparisons(updates));
        assertEquals(List.of("", "source:A vs source:B", "source:A vs source:C",
                "source:B vs source:C", "source:B vs source:C"),
                updates.stream().map(update -> update.fields().get("Compared vacancies")).toList());
        assertEquals("Complete", updates.getLast().action());
    }

    @Test
    void shrinksMaximumWhenDuplicatePreventsFutureComparisons() {
        List<ProgressSnapshot> updates = run(List.of(vacancy(1, "A", "A", false, null),
                vacancy(2, "B", "A", false, null), vacancy(3, "C", "A", false, null)),
                (a, b) -> true);

        assertEquals(List.of("0 / 3", "1 / 2", "2 / 2", "2 / 2"), comparisons(updates));
        assertEquals(List.of("source:A vs source:B", "source:A vs source:C"),
                updates.subList(1, 3).stream().map(update -> update.fields().get("Compared vacancies")).toList());
    }

    @Test
    void failedPairCountsAsCompletedAndUnresolvedVacancyIsNotSavedAsNonduplicate() {
        List<String> decisions = new ArrayList<>();
        List<ProgressSnapshot> updates = run(List.of(vacancy(1, "A", "A", false, null),
                vacancy(2, "B", "B", false, null), vacancy(3, "C", "A", false, null)), (a, b) -> {
            if (a.equals("A") && b.equals("B")) throw new IllegalStateException("model error");
            return a.equals(b);
        }, decisions);

        assertEquals(List.of("0 / 3", "1 / 3", "2 / 2", "2 / 2"), comparisons(updates));
        assertEquals("source:A vs source:C", updates.get(2).fields().get("Compared vacancies"));
        assertFalse(decisions.contains("2:null"));
        assertTrue(decisions.contains("3:1"));
    }

    @Test
    void checkedSamplesAreRestoredAndCheckedDuplicatesAreSkipped() {
        List<ProgressSnapshot> updates = run(List.of(
                vacancy(1, "A", "A", true, null), vacancy(2, "B", "A", true, 1L),
                vacancy(3, "C", "B", false, null)), (a, b) -> false);

        assertEquals(List.of("0 / 1", "1 / 1", "1 / 1"), comparisons(updates));
        assertEquals("source:A vs source:C", updates.get(1).fields().get("Compared vacancies"));
    }

    @Test
    void emptyInputPublishesZeroTotalAndSnapshotsAreImmutableAndRetained() {
        List<ProgressSnapshot> empty = run(List.of(), (a, b) -> fail("no comparisons expected"));
        assertEquals(List.of("0 / 0", "0 / 0"), comparisons(empty));

        List<ProgressSnapshot> updates = run(List.of(vacancy(1, "A", "A", false, null),
                vacancy(2, "B", "B", false, null)), (a, b) -> false);
        ProgressSnapshot first = updates.getFirst();
        assertInstanceOf(ImmutableProgressSnapshot.class, first);
        assertEquals("batch-7", first.runId());
        assertEquals(Map.of("Comparisons", "0 / 1", "Compared vacancies", ""), first.fields());
        assertThrows(UnsupportedOperationException.class, () -> first.fields().put("x", "y"));
        assertEquals("0 / 1", first.fields().get("Comparisons"));
    }

    private static List<ProgressSnapshot> run(List<VacancySnapshot> input,
                                               java.util.function.BiPredicate<String, String> compare) {
        return run(input, compare, new ArrayList<>());
    }

    private static List<ProgressSnapshot> run(List<VacancySnapshot> input,
                                               java.util.function.BiPredicate<String, String> compare,
                                               List<String> decisions) {
        List<ProgressSnapshot> updates = new ArrayList<>();
        SemanticDuplicateBatch.process(input, compare, (duplicate, original) ->
                decisions.add(duplicate + ":" + original), "batch-7", updates::add);
        return updates;
    }

    private static List<String> comparisons(List<ProgressSnapshot> updates) {
        return updates.stream().map(update -> update.fields().get("Comparisons")).toList();
    }
}
