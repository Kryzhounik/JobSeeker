package com.seeker.easyreasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EasyReasoningSessionTest {
    @TempDir
    Path root;

    @Test
    void cleanVacancyKeepsDashAndMultilineTextAcrossSessions() throws Exception {
        configureDisabledTrimming();
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();

        try (var api = new EasyReasoningApi(root)) {
            int stopExitCode = EasyReasoningCli.run(api,
                    reader("clean-vacancy -starts with dash\n"
                            + "clean-vacancy \"first line\nstop\nlast line\"\n"
                            + "stop\nclean-vacancy ignored after stop\n"),
                    new PrintWriter(output), new PrintWriter(errors));

            assertEquals(0, stopExitCode);
            assertEquals("-starts with dash" + System.lineSeparator()
                            + "first line\nstop\nlast line" + System.lineSeparator(),
                    output.toString());

            int eofExitCode = EasyReasoningCli.run(api,
                    reader("clean-vacancy after EOF\n"),
                    new PrintWriter(output), new PrintWriter(errors));

            assertEquals(0, eofExitCode);
            assertFalse(errors.toString().contains("Stopped."));
        }
    }

    @Test
    void missingArgumentReturnsParseCodeAndLaterSuccessDoesNotEraseIt() throws Exception {
        configureDisabledTrimming();
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();

        try (var api = new EasyReasoningApi(root)) {
            int exitCode = EasyReasoningCli.run(api,
                    reader("clean-vacancy\nclean-vacancy valid text\nstop\n"),
                    new PrintWriter(output), new PrintWriter(errors));

            assertEquals(2, exitCode);
            assertEquals("valid text" + System.lineSeparator(), output.toString());
            assertFalse(errors.toString().isBlank());
        }
    }

    private void configureDisabledTrimming() throws Exception {
        Path config = root.resolve("EasyReasoning/easy-reasoning.properties");
        Files.createDirectories(config.getParent());
        Files.writeString(config, "vacancy.trimming.enabled=false\n");
    }

    private BufferedReader reader(String input) {
        return new BufferedReader(new StringReader(input));
    }
}
