package com.seeker.tools.cli;

import com.google.gson.JsonParser;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
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

        PrintWriter out() {
            return new PrintWriter(stdout, true);
        }

        PrintWriter err() {
            return new PrintWriter(stderr, true);
        }

        String stdout() {
            return stdout.toString();
        }

        String stderr() {
            return stderr.toString();
        }
    }
}
