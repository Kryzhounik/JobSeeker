package com.seeker.easyreasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import com.seeker.tools.api.ProgressSnapshot;
import static org.junit.jupiter.api.Assertions.*;

class SemanticDuplicateBatchTest {
    private VacancySnapshot vacancy(long id, String text) {
        return new VacancySnapshot(id, "linkedin", "job-" + id, 1L, " Java   Engineer ", text, false, null);
    }

    @Test void comparesSequentiallyInSuppliedOrderWithoutChains() {
        List<String> calls = new ArrayList<>(), links = new ArrayList<>();
        List<VacancySnapshot> input = List.of(vacancy(20,"A"),
                new VacancySnapshot(3, "linkedin", "job-3",1L,"java engineer","A",false,null),
                vacancy(4,"C"), vacancy(5,"C"),
                new VacancySnapshot(6, "linkedin", "job-6",2L,"java engineer","A",false,null),
                new VacancySnapshot(7, "linkedin", "job-7",1L,"Java Engineer II","A",false,null),
                new VacancySnapshot(8, "linkedin", "job-8",null,"java engineer","A",false,null),
                new VacancySnapshot(9, "linkedin", "job-9",1L," ","A",false,null));
        Thread caller = Thread.currentThread();
        SemanticDuplicateBatch.process(input, (a,b) -> {
            assertSame(caller, Thread.currentThread());
            calls.add(a + ":" + b); return a.equals(b);
        }, (duplicate, original) -> links.add(duplicate + ":" + original));
        assertEquals(List.of("A:A","A:C","A:C","C:C"), calls);
        assertEquals(List.of("20:null", "3:20", "4:null", "5:4", "6:null", "7:null", "8:null", "9:null"), links);
        assertEquals(8, input.size());
    }

    @Test void failedPairDoesNotBlockLaterConfirmedMatchAndUnresolvedMatchCannotFormChain() {
        List<String> links = new ArrayList<>();
        var output = new ByteArrayOutputStream();
        var previous = System.err;
        try (var errors = new PrintStream(output)) {
            System.setErr(errors);
            SemanticDuplicateBatch.process(List.of(vacancy(1,"A"),vacancy(2,"B"),vacancy(3,"A")),
                    (a,b) -> {
                        if (a.equals("A") && b.equals("B")) throw new IllegalArgumentException("invalid model answer");
                        return a.equals(b);
                    }, (duplicate,original) -> links.add(duplicate + ":" + original));
        } finally { System.setErr(previous); }
        assertEquals(List.of("1:null", "3:1"), links);
        assertTrue(output.toString().contains("linkedin:job-1 vs linkedin:job-2"));
        assertTrue(output.toString().contains("invalid model answer"));
    }

    @Test void failedComparisonDoesNotCompleteNegativeDecision() {
        List<String> decisions = new ArrayList<>();
        SemanticDuplicateBatch.process(List.of(vacancy(1, "A"), vacancy(2, "B")),
                (a,b) -> { throw new IllegalArgumentException("comparison failed"); },
                (duplicate, original) -> decisions.add(duplicate + ":" + original));
        assertEquals(List.of("1:null"), decisions);
    }

    @Test void continuesPastFailedSampleToConfirmedResolvedOriginal() {
        List<String> decisions = new ArrayList<>();
        SemanticDuplicateBatch.process(List.of(vacancy(1, "A"), vacancy(2, "B"), vacancy(3, "C")),
                (a,b) -> {
                    if (a.equals("A") && b.equals("C")) throw new IllegalArgumentException("comparison failed");
                    return a.equals("B") && b.equals("C");
                }, (duplicate, original) -> decisions.add(duplicate + ":" + original));
        assertEquals(List.of("1:null", "2:null", "3:2"), decisions);
    }

    @Test void failedOriginalCannotBeUsedToCreateDuplicateChain() {
        List<String> decisions = new ArrayList<>();
        SemanticDuplicateBatch.process(List.of(vacancy(1, "A"), vacancy(2, "B"), vacancy(3, "B")),
                (a,b) -> {
                    if (a.equals("A") && b.equals("B")) throw new IllegalArgumentException("comparison failed");
                    return a.equals(b);
                }, (duplicate, original) -> decisions.add(duplicate + ":" + original));
        assertEquals(List.of("1:null"), decisions);
    }

    @Test void checkedNonduplicatesRestoreSamplesAndCheckedDuplicatesAreSkipped() {
        List<String> calls = new ArrayList<>(), decisions = new ArrayList<>();
        List<VacancySnapshot> input = List.of(
                new VacancySnapshot(1, "linkedin", "one", 1L, "Java Engineer", "earlier sample", true, null),
                new VacancySnapshot(2, "linkedin", "two", 1L, "java engineer", "already duplicate", true, 1L),
                vacancy(3, "earlier sample"), vacancy(4, "new description"));
        SemanticDuplicateBatch.process(input, (a,b) -> { calls.add(a + ":" + b); return a.equals(b); },
                (duplicate, original) -> decisions.add(duplicate + ":" + original));
        assertEquals(List.of("earlier sample:earlier sample", "earlier sample:new description"), calls);
        assertEquals(List.of("3:1", "4:null"), decisions);
    }

    @Test void fullyCheckedBatchDoesNotCompareOrWrite() {
        List<VacancySnapshot> input = List.of(
                new VacancySnapshot(1, "linkedin", "one", 1L, "Java Engineer", "sample", true, null),
                new VacancySnapshot(2, "linkedin", "two", 1L, "Java Engineer", "duplicate", true, 1L));
        SemanticDuplicateBatch.process(input, (a,b) -> { fail("Checked records must not be compared"); return false; },
                (duplicate, original) -> fail("Checked records must not be persisted again"));
    }

    @Test void oversizedPairExcludesLongerByUnicodeCodePointsAndRecomputesProgress() {
        var ascii = vacancy(1, "a".repeat(200));
        var chinese = vacancy(2, "漢".repeat(50));
        var later = vacancy(3, "later text");
        List<String> comparisons = new ArrayList<>(), decisions = new ArrayList<>();
        List<ProgressSnapshot> updates = new ArrayList<>();
        var output = new ByteArrayOutputStream();
        var previous = System.err;
        try (var errors = new PrintStream(output)) {
            System.setErr(errors);
            SemanticDuplicateBatch.process(List.of(ascii, chinese, later), (first, second) -> {
                comparisons.add(first + ":" + second);
                if (first.equals(ascii.text()) && second.equals(chinese.text()))
                    throw new RequestTooLargeException(3900, 4096, 256);
                return false;
            }, (duplicate, original) -> decisions.add(duplicate + ":" + original), "batch", updates::add);
        } finally { System.setErr(previous); }

        assertEquals(List.of(ascii.text() + ":" + chinese.text(), chinese.text() + ":" + later.text()), comparisons);
        assertEquals(List.of("1:null", "2:null", "3:null"), decisions);
        assertEquals(List.of("0 / 3", "0 / 1", "1 / 1", "1 / 1"),
                updates.stream().map(update -> update.fields().get("Comparisons")).toList());
        assertTrue(output.toString().contains("linkedin:job-1"));
        assertTrue(output.toString().contains("request_tokens=3900 context_limit=4096 response_tokens=256"));
        assertFalse(output.toString().contains(ascii.text()));
        assertFalse(output.toString().contains(chinese.text()));
    }

    @Test void excludesLongerCurrentVacancyWithoutMarkingItAndContinuesLaterPair() {
        var sample = vacancy(1, "short");
        var longer = vacancy(2, "z".repeat(100));
        var later = vacancy(3, "third");
        List<String> comparisons = new ArrayList<>(), decisions = new ArrayList<>();
        SemanticDuplicateBatch.process(List.of(sample, longer, later), (first, second) -> {
            comparisons.add(first + ":" + second);
            if (second.equals(longer.text())) throw new RequestTooLargeException(4000, 4096, 128);
            return false;
        }, (duplicate, original) -> decisions.add(duplicate + ":" + original));

        assertEquals(List.of("short:" + "z".repeat(100), "short:third"), comparisons);
        assertEquals(List.of("1:null", "3:null"), decisions);
        assertFalse(decisions.stream().anyMatch(decision -> decision.startsWith("2:")));
    }

    @Test void contextExclusionDoesNotClearPreviouslyCheckedDecision() {
        var savedSample = new VacancySnapshot(1, "linkedin", "saved", 1L,
                "Java Engineer", "s".repeat(200), true, null);
        var savedDuplicate = new VacancySnapshot(2, "linkedin", "linked", 1L,
                "Java Engineer", "linked description", true, 1L);
        var incoming = vacancy(3, "short");
        List<String> writes = new ArrayList<>();
        SemanticDuplicateBatch.process(List.of(savedSample, savedDuplicate, incoming), (first, second) -> {
            throw new RequestTooLargeException(4000, 4096, 128);
        }, (duplicate, original) -> writes.add(duplicate + ":" + original));

        assertEquals(List.of("3:null"), writes);
    }

    @Test void retryRestoresSavedNonduplicateAsComparisonSample() {
        List<String> decisions = new ArrayList<>();
        List<VacancySnapshot> retry = List.of(
                new VacancySnapshot(1, "linkedin", "one", 1L, "Java Engineer", "A", true, null),
                vacancy(2, "A"));
        SemanticDuplicateBatch.process(retry, (a,b) -> a.equals(b),
                (duplicate, original) -> decisions.add(duplicate + ":" + original));
        assertEquals(List.of("2:1"), decisions);
    }

    @Test void offSkipsRuntimeAndModel(@TempDir Path root) throws Exception {
        Path config = root.resolve("EasyReasoning/easy-reasoning.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config,"dedup.mode=off\n");
        try (var api = new EasyReasoningApi(root)) { api.deduplicateBatch("not-in-a-database"); }
    }

}
