package com.seeker.collector.linkedin.collection;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SemanticDuplicateObserverTest {
    private VacancySnapshot vacancy(long id, String text) {
        return new VacancySnapshot(id, "job-"+id, 1L, " Java   Engineer ", text);
    }

    @Test void comparesOrderedSamplesWithoutChainsOrHistoricalIdOrdering() {
        List<String> calls = new ArrayList<>();
        List<SemanticDuplicateObserver.Result> results = new ArrayList<>();
        try (var observer = new SemanticDuplicateObserver((a,b) -> {
            calls.add(a+":"+b); return a.equals(b);
        })) {
            observer.submit(vacancy(20,"A"));
            observer.submit(new VacancySnapshot(3,"job-3",1L,"java engineer","A"));
            observer.submit(vacancy(4,"C"));
            observer.submit(vacancy(5,"C"));
            observer.submit(new VacancySnapshot(6,"job-6",2L,"java engineer","A"));
            observer.submit(new VacancySnapshot(7,"job-7",1L,"Java Engineer II","A"));
            observer.submit(new VacancySnapshot(8,"job-8",null,"java engineer","A"));
            observer.submit(new VacancySnapshot(9,"job-9",1L," ","A"));
            observer.drain(true, results::add);
        }
        assertEquals(List.of("A:A","A:C","A:C","C:C"), calls);
        assertEquals(20, results.get(1).original().id());
        assertNull(results.get(2).original());
        assertEquals(4, results.get(3).original().id());
        assertEquals(8, results.size());
    }

    @Test void doesNotBlockSubmissionAndKeepsFailedPairAsDifferent() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        List<SemanticDuplicateObserver.Result> results = new ArrayList<>();
        try (var observer = new SemanticDuplicateObserver((a,b) -> {
            if (a.equals("A") && b.equals("B")) {
                entered.countDown();
                try { if (!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("test timed out"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                throw new IllegalArgumentException("invalid model answer");
            }
            return a.equals(b);
        })) {
            try {
                observer.submit(vacancy(1,"A"));
                observer.submit(vacancy(2,"B"));
                assertTrue(entered.await(5,TimeUnit.SECONDS));
                observer.submit(vacancy(3,"B"));
                observer.drain(false,results::add);
                assertEquals(1,results.size());
            } finally { release.countDown(); }
            observer.drain(true,results::add);
            assertNull(results.get(1).original());
            assertTrue(results.get(1).errors().getFirst().contains("job-1 vs job-2"));
            assertEquals(2,results.get(2).original().id());
            assertFalse(observer.hasPending());
        }
    }

    @Test void collectorPersistsOnItsOwnThreadAndDrainsOnSuccessAndStop() throws Exception {
        for (boolean stopped : List.of(false,true)) {
            Thread collectorThread = Thread.currentThread();
            CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
            List<String> calls = new ArrayList<>();
            var python = (com.seeker.collector.linkedin.python.PythonGateway) java.lang.reflect.Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{com.seeker.collector.linkedin.python.PythonGateway.class},
                    (proxy, method, args) -> {
                        assertSame(collectorThread, Thread.currentThread());
                        switch (method.getName()) {
                            case "semanticSnapshot":
                                calls.add("snapshot:"+args[1]);
                                return vacancy(Long.parseLong((String)args[1]),"same text");
                            case "saveSemanticDuplicate": calls.add("link:"+args[0]+":"+args[1]); return null;
                            case "finishRun": calls.add("finish"); return null;
                            case "priorityCompanyIds": throw new IllegalStateException("browser stopped");
                            default: throw new AssertionError(method.getName());
                        }
                    });
            var config = new com.seeker.collector.linkedin.config.CollectorConfig(
                    "Java",List.of("Poland"),List.of(),List.of("remote"),List.of(),List.of(),
                    "","",2,0,"chrome",90,60,3,20);
            List<com.seeker.tools.api.ProgressSnapshot> progress = new ArrayList<>();
            try (var observer = new SemanticDuplicateObserver((a,b) -> {
                assertNotSame(collectorThread,Thread.currentThread());
                entered.countDown();
                try { if (!release.await(5,TimeUnit.SECONDS)) throw new AssertionError("test timed out"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                if (stopped) throw new IllegalArgumentException("model unavailable");
                return true;
            })) {
                var collector = new LinkedInCollector(null,python,config,"run",progress::add);
                collector.observeWith(observer);
                var record = LinkedInCollector.class.getDeclaredMethod("recordResult",ProcessResult.class);
                record.setAccessible(true);
                try {
                    record.invoke(collector,new ProcessResult("linkedin","20","Java Engineer","raw_saved",""));
                    record.invoke(collector,new ProcessResult("linkedin","99","Java Engineer","content_filtered","rejected"));
                    record.invoke(collector,new ProcessResult("linkedin","3","Java Engineer","raw_saved",""));
                    assertTrue(entered.await(5,TimeUnit.SECONDS));
                    assertEquals(List.of("snapshot:20","snapshot:3"),calls);
                } finally { release.countDown(); }
                CollectionReport report = stopped ? collector.runBatch() : collector.runFromUrls(List.of());
                assertEquals(2,report.acceptedCount());
                assertEquals(List.of("20","3"),report.scope().stream().map(ScopeItem::jobId).toList());
                assertEquals(1,report.outcomes().get("content_filtered"));
                assertEquals("finish",calls.getLast());
                assertFalse(observer.hasPending());
                assertTrue(progress.stream().anyMatch(p -> p.action().equals("Waiting for description comparisons")));
                if (stopped) {
                    assertEquals("blocked",report.status());
                    assertTrue(report.message().contains("browser stopped"));
                    assertTrue(report.message().contains("model unavailable"));
                    assertFalse(calls.stream().anyMatch(c -> c.startsWith("link:")));
                } else {
                    assertEquals("complete",report.status());
                    assertEquals("link:3:20",calls.get(calls.size()-2));
                }
            }
        }
    }
}
