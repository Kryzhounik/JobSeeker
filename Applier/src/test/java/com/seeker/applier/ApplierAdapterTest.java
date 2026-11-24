package com.seeker.applier;

import com.google.gson.JsonParser;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.tools.cli.CliRunner;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplierAdapterTest {
    private static final String URL = "https://example.com/jobs/42";

    @Test
    void legacyUrlIsNormalizedAndPreparationDtoIsSerializedOnce() throws Exception {
        var api = mock(ApplierApi.class);
        when(api.prepare(URL)).thenReturn(new ApplierRunner.Result("filled", "Ready", false));
        var output = new Output();

        int code = run(api, output, ApplierAdapter.arguments(new String[]{URL}));

        assertEquals(0, code);
        var dto = JsonParser.parseString(output.stdout()).getAsJsonObject();
        assertEquals("filled", dto.get("status").getAsString());
        assertEquals("Ready", dto.get("note").getAsString());
        assertFalse(dto.get("submissionObserved").getAsBoolean());
        assertEquals("", output.stderr());
        verify(api).prepare(URL);
        verifyNoMoreInteractions(api);
    }

    @Test
    void coverLetterTextIsPrintedRawUnicode() throws Exception {
        var api = mock(ApplierApi.class);
        when(api.coverLetter(42)).thenReturn("Привет — café");
        var output = new Output();

        int code = run(api, output, "cover-letter", "42");

        assertEquals(0, code);
        assertEquals("Привет — café" + System.lineSeparator(), output.stdout());
        assertEquals("", output.stderr());
        verify(api).coverLetter(42);
        verifyNoMoreInteractions(api);
    }

    @Test
    void outputPathIsPrintedRawAndDoesNotLeakAcrossCommands() throws Exception {
        var api = mock(ApplierApi.class);
        Path firstPath = Path.of("tmp", "letter-1.txt");
        when(api.coverLetter(42, firstPath)).thenReturn(firstPath);
        when(api.coverLetter(43)).thenReturn("Second letter");
        var adapter = new ApplierAdapter(api);
        var first = new Output();
        var second = new Output();

        assertEquals(0, CliRunner.run(adapter, first.out(), first.err(), "cover-letter", "42", firstPath.toString()));
        assertEquals(0, CliRunner.run(adapter, second.out(), second.err(), "cover-letter", "43"));

        assertEquals(firstPath.toString() + System.lineSeparator(), first.stdout());
        assertFalse(first.stdout().trim().startsWith("\""));
        assertEquals("Second letter" + System.lineSeparator(), second.stdout());
        assertEquals("", first.stderr());
        assertEquals("", second.stderr());
        verify(api).coverLetter(42, firstPath);
        verify(api).coverLetter(43);
        verifyNoMoreInteractions(api);
    }

    @Test
    void malformedCliArgumentsReturnTwoWithoutCallingApi() {
        var api = mock(ApplierApi.class);
        assertInvalid(api);
        assertInvalid(api, "prepare");
        assertInvalid(api, "prepare", URL, "extra");
        assertInvalid(api, "cover-letter");
        assertInvalid(api, "cover-letter", "not-a-number");
        assertInvalid(api, "cover-letter", "42", "one", "extra");
        assertInvalid(api, "--bogus");
        verifyNoInteractions(api);
    }

    @Test
    void operationFailureWritesOnlyMessageToStderrAndReturnsOne() throws Exception {
        var api = mock(ApplierApi.class);
        when(api.prepare(URL)).thenThrow(new IllegalStateException("operation failed"));
        var output = new Output();

        int code = run(api, output, "prepare", URL);

        assertEquals(1, code);
        assertEquals("", output.stdout());
        assertEquals("operation failed" + System.lineSeparator(), output.stderr());
        verify(api).prepare(URL);
        verifyNoMoreInteractions(api);
    }

    @Test
    void usageLimitFailureReturnsThreeAndWritesDiagnosticsToStderr() throws Exception {
        var api = mock(ApplierApi.class);
        when(api.coverLetter(42)).thenThrow(new UsageLimitExceededException("quota exhausted", Instant.parse("2026-11-25T00:00:00Z")));
        var output = new Output();

        int code = run(api, output, "cover-letter", "42");

        assertEquals(3, code);
        assertEquals("", output.stdout());
        assertEquals("Codex usage limit: quota exhausted" + System.lineSeparator()
                + "Retry at: 2026-11-25T00:00:00Z" + System.lineSeparator(), output.stderr());
        verify(api).coverLetter(42);
        verifyNoMoreInteractions(api);
    }

    @Test
    void normalizationPreservesExplicitCommandsAndOptions() {
        assertArrayEquals(new String[]{"prepare", URL}, ApplierAdapter.arguments(new String[]{URL}));
        assertArrayEquals(new String[]{"prepare", URL}, ApplierAdapter.arguments(new String[]{"prepare", URL}));
        assertArrayEquals(new String[]{"cover-letter", "42"}, ApplierAdapter.arguments(new String[]{"cover-letter", "42"}));
        assertArrayEquals(new String[]{"--help"}, ApplierAdapter.arguments(new String[]{"--help"}));
    }

    private static int run(ApplierApi api, Output output, String... args) {
        return CliRunner.run(new ApplierAdapter(api), output.out(), output.err(), args);
    }

    private static void assertInvalid(ApplierApi api, String... args) {
        var output = new Output();
        assertEquals(2, run(api, output, args));
        assertEquals("", output.stdout());
    }

    private static final class Output {
        private final StringWriter stdout = new StringWriter();
        private final StringWriter stderr = new StringWriter();

        PrintWriter out() { return new PrintWriter(stdout, true); }
        PrintWriter err() { return new PrintWriter(stderr, true); }
        String stdout() { return stdout.toString(); }
        String stderr() { return stderr.toString(); }
    }
}
