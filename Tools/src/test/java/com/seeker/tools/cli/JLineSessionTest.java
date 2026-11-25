package com.seeker.tools.cli;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JLineSessionTest {
    @Test
    void preservesWhitespaceAndStopTextInsideMultilineQuotedArgument() throws Exception {
        var commands = new SessionCommands();
        var output = new Output();
        String body = "first  line\twith spaces\nstop\nlast   line";

        int exitCode = CliRunner.runSession(commands,
                reader("capture \"first  line\twith spaces\nstop\nlast   line\"\nstop\n"),
                output.out(), output.err());

        assertEquals(0, exitCode);
        assertEquals(List.of(body), commands.captured);
    }

    @Test
    void parsesMultiplePositionalsAndOptionWithoutPhantomTrailingArgument() throws Exception {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands,
                reader("pair alpha beta --mode fast   \nstop\n"),
                output.out(), output.err());

        assertEquals(0, exitCode);
        assertEquals("alpha", commands.first);
        assertEquals("beta", commands.second);
        assertEquals("fast", commands.mode);
    }

    @Test
    void acceptsClosingQuoteBeforeFollowingArguments() throws Exception {
        var commands = new SessionCommands();
        var output = new Output();

        int exitCode = CliRunner.runSession(commands,
                reader("pair \"two words\" tail --mode careful\nstop\n"),
                output.out(), output.err());

        assertEquals(0, exitCode);
        assertEquals("two words", commands.first);
        assertEquals("tail", commands.second);
        assertEquals("careful", commands.mode);
    }

    private static BufferedReader reader(String value) {
        return new BufferedReader(new StringReader(value));
    }

    @Command(name = "jline-session-test")
    static class SessionCommands {
        final List<String> captured = new ArrayList<>();
        String first;
        String second;
        String mode;

        @Command(name = "capture")
        public String capture(@Parameters(index = "0") String text) {
            captured.add(text);
            return text;
        }

        @Command(name = "pair")
        public String pair(@Parameters(index = "0") String first,
                           @Parameters(index = "1") String second,
                           @Option(names = "--mode", required = true) String mode) {
            this.first = first;
            this.second = second;
            this.mode = mode;
            return first + " " + second + " " + mode;
        }
    }

    private static final class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();

        PrintWriter out() { return new PrintWriter(stdout, true); }
        PrintWriter err() { return new PrintWriter(stderr, true); }
    }
}
