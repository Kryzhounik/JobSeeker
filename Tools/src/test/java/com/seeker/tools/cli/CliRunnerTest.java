package com.seeker.tools.cli;

import com.google.gson.JsonParser;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.tools.api.ProgressSnapshot;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliRunnerTest {
    @Test
    void serializesMethodResultDtoWithInstantAndNullFieldsOnce() {
        var commands = new Commands();
        var output = new Output();

        int exitCode = CliRunner.run(commands, output.out(), output.err(), "dto");

        assertEquals(0, exitCode);
        var json = JsonParser.parseString(output.stdout()).getAsJsonObject();
        assertEquals("2025-02-03T04:05:06Z", json.get("createdAt").getAsString());
        assertTrue(json.get("label").isJsonNull());
        assertEquals(1, commands.dtoCalls);
        assertEquals("", output.stderr());
    }

    @Test
    void writesStringMethodResultDirectly() {
        var output = new Output();

        int exitCode = CliRunner.run(new Commands(), output.out(), output.err(), "text");

        assertEquals(0, exitCode);
        assertEquals("plain response" + System.lineSeparator(), output.stdout());
        assertEquals("", output.stderr());
    }

    @Test
    void helpAndInvalidArgumentsDoNotInvokeBusinessMethods() {
        var commands = new Commands();
        var help = new Output();
        var noArgs = new Output();
        var unknown = new Output();
        var missing = new Output();

        int helpCode = CliRunner.run(commands, help.out(), help.err(), "--help");
        int noArgsCode = CliRunner.run(commands, noArgs.out(), noArgs.err());
        int unknownCode = CliRunner.run(commands, unknown.out(), unknown.err(), "missing-command");
        int missingCode = CliRunner.run(commands, missing.out(), missing.err(), "required");

        assertEquals(0, helpCode);
        assertTrue(help.stdout().contains("Usage:"));
        assertEquals(2, noArgsCode);
        assertEquals(2, unknownCode);
        assertEquals(2, missingCode);
        assertEquals(0, commands.totalBusinessCalls());
    }

    @Test
    void apiExceptionWritesOnlyItsMessageToStderrAndReturnsOne() {
        var output = new Output();

        int exitCode = CliRunner.run(new Commands(), output.out(), output.err(), "fail");

        assertEquals(1, exitCode);
        assertEquals("request failed" + System.lineSeparator(), output.stderr());
        assertEquals("", output.stdout());
        assertFalse(output.stderr().contains("IllegalStateException"));
    }

    @Test
    void helpAfterSuccessfulRunDoesNotPrintStaleExecutionResult() {
        var commands = new Commands();
        var firstOutput = new Output();
        var helpOutput = new Output();

        assertEquals(0, CliRunner.run(commands, firstOutput.out(), firstOutput.err(), "text"));
        assertEquals(0, CliRunner.run(commands, helpOutput.out(), helpOutput.err(), "--help"));

        assertEquals("plain response" + System.lineSeparator(), firstOutput.stdout());
        assertTrue(helpOutput.stdout().contains("Usage:"));
        assertFalse(helpOutput.stdout().contains("plain response"));
        assertEquals(1, commands.textCalls);
    }

    @Test
    void writesAndFlushesProgressDuringCommandBeforeFinalResult() {
        var output = new Output(false);
        var first = snapshot("run-1", "collect", "page", "1/2",
                orderedFields("page", "1", "status", "started"), "first page");
        var second = snapshot("run-1", "collect", "page", "2/2",
                orderedFields("page", "2", "status", "complete"), "second page");
        var commands = new ProgressCommands(List.of(first, second), output.stdout);

        int exitCode = CliRunner.run(commands, output.out(), output.err(), "run");

        assertEquals(0, exitCode);
        var lines = output.stdout().lines().toList();
        assertEquals(3, lines.size());
        assertProgress(lines.get(0), first);
        assertProgress(lines.get(1), second);
        var result = JsonParser.parseString(lines.get(2)).getAsJsonObject();
        assertEquals(Instant.parse("2025-02-03T04:05:06Z").toString(), result.get("createdAt").getAsString());
        assertEquals("progress complete", result.get("label").getAsString());
        assertEquals(1, commands.calls);
        assertEquals("", output.stderr());
    }

    @Test
    void keepsProgressAndReportsCommandFailureWithoutFinalResult() {
        var output = new Output(false);
        var progress = snapshot("run-2", "collect", "page", "1/2",
                orderedFields("page", "1"), "started");
        var commands = new ProgressCommands(List.of(progress), output.stdout);
        commands.failAfterProgress = true;

        int exitCode = CliRunner.run(commands, output.out(), output.err(), "run");

        assertEquals(1, exitCode);
        var lines = output.stdout().lines().toList();
        assertEquals(1, lines.size());
        assertProgress(lines.get(0), progress);
        assertEquals("request failed" + System.lineSeparator(), output.stderr());
    }

    private static ImmutableProgressSnapshot snapshot(String runId, String stage, String action,
                                                       String progress, Map<String, String> fields,
                                                       String details) {
        return new ImmutableProgressSnapshot(runId, stage, action, progress,
                new LinkedHashMap<>(fields), details);
    }

    private static Map<String, String> orderedFields(String... values) {
        var fields = new LinkedHashMap<String, String>();
        for (int i = 0; i < values.length; i += 2) fields.put(values[i], values[i + 1]);
        return fields;
    }

    private static void assertProgress(String line, ProgressSnapshot expected) {
        var json = JsonParser.parseString(line).getAsJsonObject();
        assertEquals(expected.runId(), json.get("runId").getAsString());
        assertEquals(expected.stage(), json.get("stage").getAsString());
        assertEquals(expected.action(), json.get("action").getAsString());
        assertEquals(expected.progress(), json.get("progress").getAsString());
        assertEquals(expected.fields(), new com.google.gson.Gson().fromJson(
                json.get("fields"), new com.google.gson.reflect.TypeToken<LinkedHashMap<String, String>>() {}.getType()));
        assertEquals(new ArrayList<>(expected.fields().keySet()),
                new ArrayList<>(json.getAsJsonObject("fields").keySet()));
        assertEquals(expected.details(), json.get("details").getAsString());
    }

    @Command(name = "progress", mixinStandardHelpOptions = true)
    static class ProgressCommands {
        private final List<ProgressSnapshot> updates;
        private final StringWriter stdout;
        int calls;
        boolean failAfterProgress;

        ProgressCommands(List<ProgressSnapshot> updates, StringWriter stdout) {
            this.updates = updates;
            this.stdout = stdout;
        }

        @picocli.CommandLine.Command(name = "run")
        public Result run(@Option(names = "--_cb", hidden = true) Consumer<ProgressSnapshot> progress) {
            calls++;
            for (int i = 0; i < updates.size(); i++) {
                progress.accept(updates.get(i));
                assertTrue(stdout.toString().lines().count() == i + 1,
                        "progress must be flushed before command continues");
            }
            if (failAfterProgress) throw new IllegalStateException("request failed");
            return new Result(Instant.parse("2025-02-03T04:05:06Z"), "progress complete");
        }
    }

    @Command(name = "tools", mixinStandardHelpOptions = true)
    static class Commands {
        int dtoCalls;
        int textCalls;
        int requiredCalls;
        int failureCalls;

        @picocli.CommandLine.Command(name = "dto")
        public Result dto() {
            dtoCalls++;
            return new Result(Instant.parse("2025-02-03T04:05:06Z"), null);
        }

        @picocli.CommandLine.Command(name = "text")
        public String text() {
            textCalls++;
            return "plain response";
        }

        @picocli.CommandLine.Command(name = "required")
        public String required(@Option(names = "--value", required = true) String value) {
            requiredCalls++;
            return value;
        }

        @picocli.CommandLine.Command(name = "fail")
        public String fail() {
            failureCalls++;
            throw new IllegalStateException("request failed");
        }

        int totalBusinessCalls() {
            return dtoCalls + textCalls + requiredCalls + failureCalls;
        }
    }

    record Result(Instant createdAt, String label) {}

    private static final class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();
        private final boolean autoFlush;

        Output() { this(true); }

        Output(boolean autoFlush) { this.autoFlush = autoFlush; }

        PrintWriter out() {
            return new PrintWriter(stdout, autoFlush);
        }

        PrintWriter err() {
            return new PrintWriter(stderr, autoFlush);
        }

        String stdout() {
            return stdout.toString();
        }

        String stderr() {
            return stderr.toString();
        }
    }
}
