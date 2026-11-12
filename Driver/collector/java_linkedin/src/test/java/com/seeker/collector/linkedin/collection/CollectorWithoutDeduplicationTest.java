package com.seeker.collector.linkedin.collection;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CollectorWithoutDeduplicationTest {
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
