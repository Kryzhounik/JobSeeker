package com.seeker.easyreasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticDuplicateBatchTest {
    private VacancySnapshot vacancy(long id, String text) {
        return new VacancySnapshot(id, "linkedin", "job-" + id, 1L, " Java   Engineer ", text);
    }

    @Test void comparesSequentiallyInSuppliedOrderWithoutChains() {
        List<String> calls = new ArrayList<>(), links = new ArrayList<>();
        List<VacancySnapshot> input = List.of(vacancy(20,"A"),
                new VacancySnapshot(3, "linkedin", "job-3",1L,"java engineer","A"),
                vacancy(4,"C"), vacancy(5,"C"),
                new VacancySnapshot(6, "linkedin", "job-6",2L,"java engineer","A"),
                new VacancySnapshot(7, "linkedin", "job-7",1L,"Java Engineer II","A"),
                new VacancySnapshot(8, "linkedin", "job-8",null,"java engineer","A"),
                new VacancySnapshot(9, "linkedin", "job-9",1L," ","A"));
        Thread caller = Thread.currentThread();
        SemanticDuplicateBatch.process(input, (a,b) -> {
            assertSame(caller, Thread.currentThread());
            calls.add(a + ":" + b); return a.equals(b);
        }, (duplicate, original) -> links.add(duplicate + ":" + original));
        assertEquals(List.of("A:A","A:C","A:C","C:C"), calls);
        assertEquals(List.of("3:20","5:4"), links);
        assertEquals(8, input.size());
    }

    @Test void modelErrorsAreVisibleAndDoNotCreateLinksOrStopRemainingComparisons() {
        List<String> links = new ArrayList<>();
        var output = new ByteArrayOutputStream();
        var previous = System.err;
        try (var errors = new PrintStream(output)) {
            System.setErr(errors);
            SemanticDuplicateBatch.process(List.of(vacancy(1,"A"),vacancy(2,"B"),vacancy(3,"B")),
                    (a,b) -> {
                        if (a.equals("A")) throw new IllegalArgumentException("invalid model answer");
                        return a.equals(b);
                    }, (duplicate,original) -> links.add(duplicate + ":" + original));
        } finally { System.setErr(previous); }
        assertEquals(List.of("3:2"), links);
        assertTrue(output.toString().contains("linkedin:job-1 vs linkedin:job-2"));
        assertTrue(output.toString().contains("invalid model answer"));
    }

    @Test void offSkipsRuntimeAndModel(@TempDir Path root) throws Exception {
        Path config = root.resolve("EasyReasoning/easy-reasoning.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config,"dedup.mode=off\n");
        try (var api = new EasyReasoningApi(root)) { api.deduplicateBatch("not-in-a-database"); }
    }

}
