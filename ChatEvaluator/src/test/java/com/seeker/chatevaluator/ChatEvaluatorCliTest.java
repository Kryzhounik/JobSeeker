package com.seeker.chatevaluator;

import com.seeker.tools.cli.CliRunner;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatEvaluatorCliTest {
    private static final String URL = "https://example.invalid/jobs/123";
    private final ChatEvaluatorApi api = mock(ChatEvaluatorApi.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
    private final PrintWriter stdout = new PrintWriter(output, true, StandardCharsets.UTF_8);
    private final PrintWriter stderr = new PrintWriter(errors, true, StandardCharsets.UTF_8);

    private int run(String... args) {
        return CliRunner.run(new ChatEvaluatorAdapter(api), stdout, stderr, args);
    }

    @Test
    void delegatesOnceAndPrintsRawUnicodeMultilineString() throws Exception {
        String answer = "Результат оценки — 👍\nПервый абзац.\n\nВторой абзац.";
        when(api.evaluate(URL)).thenReturn(answer);

        assertEquals(0, run("evaluate", URL));

        assertEquals(answer + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        verify(api).evaluate(URL);
        verifyNoMoreInteractions(api);
        assertEquals("", errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void rejectsMissingExtraAndHelpArgumentsWithoutCallingApi() {
        for (String[] args : new String[][]{
                {"evaluate"}, {"evaluate", URL, "extra"}, {"--help"}, {"evaluate", "--help"}
        }) {
            assertEquals(2, run(args));
        }

        verifyNoInteractions(api);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Usage:"));
    }

    @Test
    void realApiRejectsInvalidUrlsAndRunnerReturnsApiError(@TempDir Path root) {
        ChatEvaluatorApi realApi = new ChatEvaluatorApi(root);
        for (String url : new String[]{null, "not a URL", "file:///jobs/123"}) {
            assertThrows(IllegalArgumentException.class, () -> realApi.evaluate(url));
            assertThrows(IllegalArgumentException.class, () -> realApi.evaluate(url, 123));
        }

        assertEquals(1, CliRunner.run(new ChatEvaluatorAdapter(realApi), stdout, stderr,
                "evaluate", "not a URL"));
        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertTrue(errors.toString(StandardCharsets.UTF_8).contains("Expected one HTTP(S) vacancy URL."));
    }

    @Test
    void operationalFailureReturnsOneWithErrorOnlyOnStderr() throws Exception {
        when(api.evaluate(URL)).thenThrow(new IllegalStateException("evaluation failed"));

        assertEquals(1, run("evaluate", URL));

        assertEquals("", output.toString(StandardCharsets.UTF_8));
        assertEquals("evaluation failed" + System.lineSeparator(), errors.toString(StandardCharsets.UTF_8));
    }

    @Test
    void adapterCanBeReusedAcrossCallsWithSameApi() throws Exception {
        String first = "Первый ответ";
        String second = "Второй ответ";
        when(api.evaluate(URL)).thenReturn(first, second);
        ChatEvaluatorAdapter adapter = new ChatEvaluatorAdapter(api);

        assertEquals(0, CliRunner.run(adapter, stdout, stderr, "evaluate", URL));
        assertEquals(0, CliRunner.run(adapter, stdout, stderr, "evaluate", URL));

        assertEquals(first + System.lineSeparator() + second + System.lineSeparator(),
                output.toString(StandardCharsets.UTF_8));
        verify(api, times(2)).evaluate(URL);
        verifyNoMoreInteractions(api);
    }

    @Test
    void cliDoesNotAcceptJobIdArgument() {
        assertEquals(2, run("evaluate", URL, "42"));
        verifyNoInteractions(api);
        assertEquals("", output.toString(StandardCharsets.UTF_8));
    }
}
