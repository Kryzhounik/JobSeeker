package com.seeker.collector.core;

import com.google.gson.JsonParser;
import com.seeker.collector.common.CollectionReport;
import com.seeker.collector.linkedin.login.LoginResult;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.tools.cli.CliRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CollectorAdapterTest {
    @Test
    void loginDelegatesOnlyToApiAndEmitsResult() throws Exception {
        var api = mock(CollectorApi.class);
        var result = new LoginResult("linkedin", "ready", "default", "logged in");
        when(api.login()).thenReturn(result);
        var output = new Output();

        int exitCode = CliRunner.run(new CollectorAdapter(api),
                output.out(), output.err(), "login");

        assertEquals(0, exitCode);
        var json = JsonParser.parseString(output.stdout()).getAsJsonObject();
        assertEquals("linkedin", json.get("source").getAsString());
        assertEquals("ready", json.get("status").getAsString());
        assertEquals("", output.stderr());
        verify(api).login();
        verifyNoMoreInteractions(api);
    }

    @Test
    void collectBatchForwardsProgressBeforeReturningReport() throws Exception {
        var api = mock(CollectorApi.class);
        var output = new Output();
        var progress = new ImmutableProgressSnapshot("run-1", "collect", "page", "1/1",
                orderedFields("page", "1", "accepted", "2"), "collected page");
        var report = new CollectionReport("linkedin", "run-1", "complete", 2,
                List.of(), Map.of(), List.of(), "done");
        doAnswer(invocation -> {
                    Consumer<ProgressSnapshot> callback = invocation.getArgument(0);
                    callback.accept(progress);
                    assertTrue(output.stdout().contains("\"details\":\"collected page\""),
                            "progress must be flushed before collectBatch returns");
                    return report;
                })
                .when(api).collectBatch(org.mockito.ArgumentMatchers.<Consumer<ProgressSnapshot>>any());

        int exitCode = CliRunner.run(new CollectorAdapter(api),
                output.out(), output.err(), "collect-batch");

        assertEquals(0, exitCode);
        var lines = output.stdout().lines().toList();
        assertEquals(2, lines.size());
        var progressJson = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals("run-1", progressJson.get("runId").getAsString());
        assertEquals("collected page", progressJson.get("details").getAsString());
        assertEquals(List.of("page", "accepted"),
                progressJson.getAsJsonObject("fields").keySet().stream().toList());
        var resultJson = JsonParser.parseString(lines.get(1)).getAsJsonObject();
        assertEquals("run-1", resultJson.get("run_id").getAsString());
        assertEquals(2, resultJson.get("accepted_count").getAsInt());
        assertEquals("", output.stderr());
        verify(api).collectBatch(org.mockito.ArgumentMatchers.<Consumer<ProgressSnapshot>>any());
        verifyNoMoreInteractions(api);
    }

    private static Map<String, String> orderedFields(String... values) {
        var fields = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) fields.put(values[i], values[i + 1]);
        return fields;
    }

    private static final class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();

        PrintWriter out() { return new PrintWriter(stdout, false); }
        PrintWriter err() { return new PrintWriter(stderr, false); }
        String stdout() { return stdout.toString(); }
        String stderr() { return stderr.toString(); }
    }
}
