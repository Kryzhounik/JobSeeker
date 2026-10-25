package com.seeker.easyreasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class VacancyCleanerTest {
    @TempDir Path diagnostics;

    @BeforeEach
    void writePrompt() throws Exception {
        Files.writeString(diagnostics.resolve("prompt.txt"), "Настраиваемый промпт\nNumbered source:\n");
    }

    private VacancyCleaner cleaner(boolean enabled, Function<String, String> generate) {
        return new VacancyCleaner(enabled, new EasyReasoningService() {
            public String generate(String prompt) { return generate.apply(prompt); }
            public void close() {}
        }, diagnostics.resolve("samples"), diagnostics.resolve("prompt.txt"));
    }

    @Test
    void disabledReturnsOriginalWithoutModelOrDiagnostics() throws Exception {
        Files.delete(diagnostics.resolve("prompt.txt"));
        String original = "  Навигация\r\nJava developer\nПодвал  ";
        assertSame(original, cleaner(false, prompt -> { throw new AssertionError("Model called"); })
                .cleanVacancy(original));
        assertFalse(Files.exists(diagnostics.resolve("samples")));
    }

    @Test
    void readsExternalUtf8PromptForEachCallAndReportsMissingFile() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        var cleaner = cleaner(true, prompt -> {
            received.set(prompt);
            return "{\"from_line\":1,\"to_line\":1}";
        });
        cleaner.cleanVacancy("Вакансия");
        assertEquals("Настраиваемый промпт\nNumbered source:\n1: Вакансия\n", received.get());
        Files.writeString(diagnostics.resolve("prompt.txt"), "Изменённый промпт");
        cleaner.cleanVacancy("Вакансия");
        assertEquals("Изменённый промпт\n1: Вакансия\n", received.get());
        Files.delete(diagnostics.resolve("prompt.txt"));
        assertThrows(java.io.UncheckedIOException.class, () -> cleaner.cleanVacancy("Вакансия"));
    }

    @Test
    void retainsExactContiguousSourceIncludingMixedLineEndingsAndWhitespace() throws Exception {
        String original = "Вступление\r\n  Java developer \n\tТребования: Java\rПодвал\n";
        AtomicReference<String> prompt = new AtomicReference<>();
        String response = "{\"from_line\":2,\"to_line\":3}";
        String result = cleaner(true, value -> { prompt.set(value); return response; }).cleanVacancy(original);
        assertEquals("  Java developer \n\tТребования: Java\r", result);
        assertTrue(prompt.get().contains("1: Вступление\r\n2:   Java developer \n3: \tТребования: Java\r4: Подвал\n"));
        try (var files = Files.list(diagnostics.resolve("samples"))) {
            var saved = files.toList();
            assertEquals(3, saved.size());
            for (var file : saved) {
                String expected = file.toString().endsWith(".before.txt") ? original
                        : file.toString().endsWith(".after.txt") ? result : response;
                assertEquals(expected, Files.readString(file));
            }
        }
    }

    @Test
    void supportsUnterminatedLinesAndDoesNotInventALineAfterFinalNewline() {
        for (String original : List.of("first\nsecond", "first\r\nsecond\r\n", "first\rsecond\r")) {
            assertEquals(original, cleaner(true, prompt -> "{\"from_line\":1,\"to_line\":2}").cleanVacancy(original));
            assertThrows(IllegalStateException.class,
                    () -> cleaner(true, prompt -> "{\"from_line\":1,\"to_line\":3}").cleanVacancy(original));
        }
        assertEquals("", cleaner(true, prompt -> { throw new AssertionError("Empty input sent"); }).cleanVacancy(""));
        assertEquals("only", cleaner(true, prompt -> "{\"from_line\":1,\"to_line\":1}").cleanVacancy("only"));
    }

    @Test
    void invalidModelBoundariesFailInsteadOfReturningOriginal() {
        for (String response : List.of("not json", "null", "[]", "{}",
                "{\"from_line\":0,\"to_line\":2}", "{\"from_line\":2,\"to_line\":1}",
                "{\"from_line\":1,\"to_line\":3}", "{\"from_line\":1.5,\"to_line\":2}",
                "{\"from_line\":\"1\",\"to_line\":2}", "{\"from_line\":1,\"to_line\":99999999999}",
                "{\"from_line\":1,\"to_line\":2} {}")) {
            assertThrows(RuntimeException.class, () -> cleaner(true, prompt -> response).cleanVacancy("one\ntwo"), response);
        }
    }

    @Test
    void acceptsOneJsonCodeBlockAndUsesOnlyBoundariesNeverRewrittenText() {
        String response = """
                ```json
                {"from_line":2,"to_line":2,"description":"A rewritten description that must not be used"}
                ```
                """;
        assertEquals("  Original Java requirements\r\n",
                cleaner(true, prompt -> response).cleanVacancy("Intro\n  Original Java requirements\r\nFooter"));
        assertThrows(RuntimeException.class, () -> cleaner(true,
                prompt -> "Explanation\n" + response).cleanVacancy("one\ntwo"));
    }

    @Test
    void modelFailurePropagates() {
        var failure = new IllegalStateException("Model unavailable");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> cleaner(true, prompt -> { throw failure; }).cleanVacancy("Java developer")));
    }
}
