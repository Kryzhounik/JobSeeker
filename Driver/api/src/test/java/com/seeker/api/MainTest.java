package com.seeker.api;

import com.seeker.applier.ApplierCli;
import com.seeker.chatevaluator.ChatEvaluatorCli;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.ToIntFunction;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {
    @Test
    void applierPublicEntryMatchesDriverRouting(@TempDir Path projectRoot) throws Exception {
        Captured directUsage = capture(projectRoot, () -> ApplierCli.execute(new String[]{"prepare"}));
        Captured routedUsage = capture(projectRoot, () -> Main.execute(new String[]{"apply", "prepare"}));
        assertEquals(2, directUsage.code());
        assertEquals(directUsage, routedUsage);
        assertEquals("", directUsage.stdout());
        assertTrue(directUsage.stderr().contains("Usage:"));

        Captured directInvalidUrl = capture(projectRoot, () -> ApplierCli.execute(new String[]{"prepare", "not-a-url"}));
        Captured routedInvalidUrl = capture(projectRoot, () -> Main.execute(new String[]{"apply", "prepare", "not-a-url"}));
        assertEquals(1, directInvalidUrl.code());
        assertEquals(directInvalidUrl, routedInvalidUrl);
        assertEquals("", directInvalidUrl.stdout());
        assertFalse(directInvalidUrl.stderr().isBlank());
    }

    @Test
    void chatEvaluatorPublicEntryMatchesDriverRouting(@TempDir Path projectRoot) throws Exception {
        Captured directUsage = capture(projectRoot, () -> ChatEvaluatorCli.execute(new String[0]));
        Captured routedUsage = capture(projectRoot, () -> Main.execute(new String[]{"chat-eval"}));
        assertEquals(2, directUsage.code());
        assertEquals(directUsage, routedUsage);
        assertEquals("", directUsage.stdout());
        assertTrue(directUsage.stderr().contains("Usage:"));

        Captured directInvalidUrl = capture(projectRoot, () -> ChatEvaluatorCli.execute(new String[]{"not-a-url"}));
        Captured routedInvalidUrl = capture(projectRoot, () -> Main.execute(new String[]{"chat-eval", "not-a-url"}));
        assertEquals(1, directInvalidUrl.code());
        assertEquals(directInvalidUrl, routedInvalidUrl);
        assertEquals("", directInvalidUrl.stdout());
        assertFalse(directInvalidUrl.stderr().isBlank());
    }

    @Test
    void forwardsCollectorCommandsIncludingTheirNames() throws Exception {
        for (String command : List.of("login", "collect-batch")) {
            String[] args = {command};
            assertRoute("collector", args, args);
        }
    }

    @Test
    void forwardsOrchestratorCommandsWithoutParsingArguments() throws Exception {
        for (String[] args : List.of(
                new String[]{"process-batch", "linkedin"},
                new String[]{"process-urls", "url-1", "url-2"},
                new String[]{"check-recommendations"},
                new String[]{"resume", "run-1"},
                new String[]{"job-facts", "run-1", "linkedin", "1,2"},
                new String[]{"finish-analysis", "run-1", "linkedin", "1,2"}
        )) {
            assertRoute("orchestrator", args, args);
        }
    }

    @Test
    void removesOnlyRouterCommandForApplierAndChatEvaluator() throws Exception {
        assertRoute("applier", new String[]{"apply", "url"}, new String[]{"url"});
        assertRoute("chat", new String[]{"chat-eval", "url"}, new String[]{"url"});
        assertRoute("applier", new String[]{"apply"}, new String[0]);
        assertRoute("chat", new String[]{"chat-eval", "bad", "extra"}, new String[]{"bad", "extra"});
    }

    @Test
    void rejectsMissingOrUnknownCommandWithoutCallingModules() throws Exception {
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
            assertEquals(2, Main.execute(new String[0], () -> unexpected.applyAsInt(new String[0]), () -> unexpected.applyAsInt(new String[0]), unexpected, unexpected));
            assertEquals(2, Main.execute(new String[]{"unknown"}, () -> unexpected.applyAsInt(new String[]{"unknown"}), () -> unexpected.applyAsInt(new String[]{"unknown"}), unexpected, unexpected));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertFalse(errors.toString(StandardCharsets.UTF_8).isBlank());
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Unknown command: unknown"));
    }

    private static void assertRoute(String expectedModule, String[] args, String[] expectedArgs) throws Exception {
        List<String> called = new ArrayList<>();
        List<String[]> forwarded = new ArrayList<>();
        int code = Main.execute(args,
                () -> { called.add("collector"); forwarded.add(args); return 17; },
                () -> { called.add("orchestrator"); forwarded.add(args); return 17; },
                rest -> { called.add("applier"); forwarded.add(rest); return 17; },
                rest -> { called.add("chat"); forwarded.add(rest); return 17; });
        assertEquals(17, code);
        assertEquals(List.of(expectedModule), called);
        assertArrayEquals(expectedArgs, forwarded.getFirst());
    }

    private static Captured capture(Path projectRoot, Callable<Integer> call) throws Exception {
        String previousProjectRoot = System.getProperty("seeker.projectRoot");
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            System.setProperty("seeker.projectRoot", projectRoot.toString());
            System.setOut(out);
            System.setErr(err);
            int code = call.call();
            return new Captured(code, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
        } finally {
            if (previousProjectRoot == null) {
                System.clearProperty("seeker.projectRoot");
            } else {
                System.setProperty("seeker.projectRoot", previousProjectRoot);
            }
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record Captured(int code, String stdout, String stderr) {}
}
