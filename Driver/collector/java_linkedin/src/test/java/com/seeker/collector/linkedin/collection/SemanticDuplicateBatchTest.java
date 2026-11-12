package com.seeker.collector.linkedin.collection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticDuplicateBatchTest {
    private VacancySnapshot vacancy(long id, String text) {
        return new VacancySnapshot(id, "job-" + id, 1L, " Java   Engineer ", text);
    }

    @Test void comparesSequentiallyInSuppliedOrderWithoutChains() {
        List<String> calls = new ArrayList<>(), links = new ArrayList<>();
        List<VacancySnapshot> input = List.of(vacancy(20,"A"),
                new VacancySnapshot(3,"job-3",1L,"java engineer","A"),
                vacancy(4,"C"), vacancy(5,"C"),
                new VacancySnapshot(6,"job-6",2L,"java engineer","A"),
                new VacancySnapshot(7,"job-7",1L,"Java Engineer II","A"),
                new VacancySnapshot(8,"job-8",null,"java engineer","A"),
                new VacancySnapshot(9,"job-9",1L," ","A"));
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
        assertTrue(output.toString().contains("job-1 vs job-2"));
        assertTrue(output.toString().contains("invalid model answer"));
    }

    @Test void offSkipsRuntimeAndModel(@TempDir Path root) throws Exception {
        Path config = root.resolve("EasyReasoning/easy-reasoning.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config,"dedup.mode=off\n");
        new SemanticDuplicateBatch(root).processBatch("not-in-a-database");
    }

    @Test void collectorAcceptsVacanciesWithoutInvokingSemanticOperations() throws Exception {
        var python = (com.seeker.collector.linkedin.python.PythonGateway) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{com.seeker.collector.linkedin.python.PythonGateway.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("finishRun")) return null;
                    throw new AssertionError("Unexpected collector operation: " + method.getName());
                });
        var config = new com.seeker.collector.linkedin.config.CollectorConfig(
                "Java",List.of("Poland"),List.of(),List.of("remote"),List.of(),List.of(),
                "","",2,0,"chrome",90,60,3,20);
        var collector = new LinkedInCollector(null,python,config,"run");
        var record = LinkedInCollector.class.getDeclaredMethod("recordResult",ProcessResult.class);
        record.setAccessible(true);
        record.invoke(collector,new ProcessResult("linkedin","20","Java Engineer","raw_saved",""));
        record.invoke(collector,new ProcessResult("linkedin","99","Java Engineer","content_filtered","rejected"));
        record.invoke(collector,new ProcessResult("linkedin","3","Java Engineer","raw_saved",""));
        var result = collector.runFromUrls(List.of());
        assertEquals(2,result.acceptedCount());
        assertEquals(List.of("20","3"),result.scope().stream().map(ScopeItem::jobId).toList());
        assertEquals("complete", result.status());
    }
}
