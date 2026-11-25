package com.seeker.tools.cli;

import com.seeker.codex.UsageLimitExceededException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliSessionTest {
    @Test
    void runsCommandsOnOneAdapterAndPreservesMultilineQuotedText() throws IOException {
        var commands = new SessionCommands();
        var output = new Output();
        var input = reader("capture first body with spaces\n"
                + "capture \"line one\nline two with \\\"quotes\\\" and C:\\\\Users\\\\sam\\\\job.txt\"\n"
                + "stop\n"
                + "capture ignored\n");

        int exitCode = CliRunner.runSession(commands, input, output.out(), output.err());

        assertEquals(0, exitCode);
        assertEquals(List.of("first body with spaces",
                "line one\nline two with \"quotes\" and C:\\Users\\sam\\job.txt"), commands.captured);
        assertEquals(2, commands.captureCalls);
    }

    @Test
    void stopAndEofEndTheSession() throws IOException {
        var stoppedCommands = new SessionCommands();
        var stoppedOutput = new Output();

        int stoppedCode = CliRunner.runSession(stoppedCommands,
                reader("capture before\n stop \n capture after\n"),
                stoppedOutput.out(), stoppedOutput.err());

        assertEquals(0, stoppedCode);
        assertEquals(List.of("before"), stoppedCommands.captured);

        var eofCommands = new SessionCommands();
        var eofOutput = new Output();
        int eofCode = CliRunner.runSession(eofCommands, reader("capture until eof"),
                eofOutput.out(), eofOutput.err());

        assertEquals(0, eofCode);
        assertEquals(List.of("until eof"), eofCommands.captured);
    }

    @Test
    void keepsLatestNonzeroSyntaxCodeAfterLaterSuccess() throws IOException {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands, reader("required\ncapture recovered\n"),
                output.out(), output.err());

        assertEquals(2, exitCode);
        assertEquals(List.of("recovered"), commands.captured);
        assertFalse(output.stderr().isBlank());
    }

    @Test
    void continuesAfterApiFailureAndKeepsFailureCode() throws IOException {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands, reader("fail\ncapture recovered\n"),
                output.out(), output.err());

        assertEquals(1, exitCode);
        assertEquals(List.of("recovered"), commands.captured);
        assertTrue(output.stderr().contains("request failed"));
    }

    @Test
    void retainsTheLatestNonzeroCodeIncludingUsageLimits() throws IOException {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands,
                reader("required\nfail\nlimit\ncapture recovered\n"), output.out(), output.err());

        assertEquals(3, exitCode);
        assertEquals(List.of("recovered"), commands.captured);
        assertTrue(output.stderr().contains("Codex usage limit: exhausted"));
    }

    @Test
    void unclosedQuoteIsASyntaxErrorAndDoesNotInvokeBusinessMethod() throws IOException {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands, reader("capture \"never closes\n"),
                output.out(), output.err());

        assertEquals(2, exitCode);
        assertEquals(0, commands.captureCalls);
        assertFalse(output.stderr().isBlank());
    }

    @Test
    void doesNotCloseInjectedStreamsAndFlushesOutput() throws IOException {
        var commands = new SessionCommands();
        var input = new TrackingReader("capture flushed\nstop\n");
        var stdout = new TrackingWriter();
        var stderr = new TrackingWriter();

        int exitCode = CliRunner.runSession(commands, input, stdout, stderr);

        assertEquals(0, exitCode);
        assertFalse(input.closed);
        assertFalse(stdout.closed);
        assertFalse(stderr.closed);
        assertTrue(stdout.flushCalls > 0);
        assertTrue(stderr.flushCalls > 0);
        assertTrue(stdout.contents().contains("flushed"));
    }

    private static BufferedReader reader(String value) {
        return new BufferedReader(new StringReader(value));
    }

    @Command(name = "session-test")
    static class SessionCommands {
        final List<String> captured = new ArrayList<>();
        int captureCalls;

        @Command(name = "capture")
        public String capture(@Parameters(arity = "1..*") String[] parts) {
            String text = String.join(" ", parts);
            captureCalls++;
            captured.add(text);
            return text;
        }

        @Command(name = "required")
        public String required(@Parameters(index = "0", arity = "1") String value) {
            return value;
        }

        @Command(name = "fail")
        public String fail() {
            throw new IllegalStateException("request failed");
        }

        @Command(name = "limit")
        public String limit() throws UsageLimitExceededException {
            throw new UsageLimitExceededException("exhausted", null);
        }
    }

    private static class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();

        PrintWriter out() { return new PrintWriter(stdout, true); }
        PrintWriter err() { return new PrintWriter(stderr, true); }
        String stdout() { return stdout.toString(); }
        String stderr() { return stderr.toString(); }
    }

    private static final class TrackingReader extends BufferedReader {
        boolean closed;

        TrackingReader(String value) {
            super(new StringReader(value));
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class TrackingWriter extends PrintWriter {
        private final StringWriter buffer;
        boolean closed;
        int flushCalls;

        TrackingWriter() {
            this(new StringWriter());
        }

        private TrackingWriter(StringWriter buffer) {
            super(buffer);
            this.buffer = buffer;
        }

        @Override
        public void flush() {
            flushCalls++;
            super.flush();
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }

        String contents() { return buffer.toString(); }
    }
}
