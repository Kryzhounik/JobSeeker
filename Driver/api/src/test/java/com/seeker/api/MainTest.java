package com.seeker.api;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @Test
    void forwardsCollectorCommandsIncludingTheirNames() {
        for (String command : List.of("login", "collect-batch")) {
            String[] args = {command};
            assertRoute("collector", args, args);
        }
    }

    @Test
    void forwardsOrchestratorCommandsWithoutParsingArguments() {
        for (String[] args : List.of(
                new String[]{"process-batch", "linkedin"},
                new String[]{"process-urls", "url-1", "url-2"},
                new String[]{"resume", "run-1"},
                new String[]{"job-facts", "run-1", "linkedin", "1,2"},
                new String[]{"finish-analysis", "run-1", "linkedin", "1,2"}
        )) {
            assertRoute("orchestrator", args, args);
        }
    }

    @Test
    void removesOnlyRouterCommandForApplierAndChatEvaluator() {
        assertRoute("applier", new String[]{"apply", "url"}, new String[]{"url"});
        assertRoute("chat", new String[]{"chat-eval", "url"}, new String[]{"url"});
        assertRoute("applier", new String[]{"apply"}, new String[0]);
        assertRoute("chat", new String[]{"chat-eval", "bad", "extra"}, new String[]{"bad", "extra"});
    }

    @Test
    void rejectsMissingOrUnknownCommandWithoutCallingModules() {
        ToIntFunction<String[]> unexpected = args -> {
            throw new AssertionError("Module must not be called");
        };
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setOut(out);
            System.setErr(err);
            assertEquals(2, Main.execute(new String[0], unexpected, unexpected, unexpected, unexpected));
            assertEquals(2, Main.execute(new String[]{"unknown"}, unexpected, unexpected, unexpected, unexpected));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertFalse(errors.toString(StandardCharsets.UTF_8).isBlank());
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Unknown command: unknown"));
    }

    private static void assertRoute(String expectedModule, String[] args, String[] expectedArgs) {
        List<String> called = new ArrayList<>();
        List<String[]> forwarded = new ArrayList<>();
        int code = Main.execute(args,
                rest -> { called.add("collector"); forwarded.add(rest); return 17; },
                rest -> { called.add("orchestrator"); forwarded.add(rest); return 17; },
                rest -> { called.add("applier"); forwarded.add(rest); return 17; },
                rest -> { called.add("chat"); forwarded.add(rest); return 17; });
        assertEquals(17, code);
        assertEquals(List.of(expectedModule), called);
        assertArrayEquals(expectedArgs, forwarded.getFirst());
    }
}
