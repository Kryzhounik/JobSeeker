package com.seeker.easyreasoning;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class VacancyDescriptionComparatorTest {
    @TempDir Path root;

    private VacancyDescriptionComparator comparator(Function<String, String> generate) throws Exception {
        Path prompt = root.resolve("prompt.md");
        Files.writeString(prompt, "Compare descriptions. Инструкция UTF-8.\n");
        return new VacancyDescriptionComparator(new EasyReasoningService() {
            public String generate(String text) { return generate.apply(text); }
            public void close() {}
        }, prompt);
    }

    @Test
    void mapsBothExactAnswersAndStripsSurroundingWhitespace() throws Exception {
        assertTrue(comparator(p -> "\u2003\nNO SUBSTANTIVE DIFFERENCES\t ").areDuplicateDescriptions("a", "b"));
        assertFalse(comparator(p -> "\n SUBSTANTIVE DIFFERENCES\r\n").areDuplicateDescriptions("a", "b"));
    }

    @Test
    void sendsExternalUtf8InstructionAndBothUnchangedTextsWithoutPreviousInput() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        var comparator = comparator(p -> { received.set(p); return "NO SUBSTANTIVE DIFFERENCES"; });
        String first = "  Первая\r\nJava \"backend\"  ";
        String second = "Second\nRemote\t";
        comparator.areDuplicateDescriptions(first, second);
        assertEquals("Compare descriptions. Инструкция UTF-8.\n\n\nAD 1:\n" + first
                + "\n\nAD 2:\n" + second, received.get());
        Files.writeString(root.resolve("prompt.md"), "Updated instructions");
        comparator.areDuplicateDescriptions("Next first", "Next second");
        assertEquals("Updated instructions\n\nAD 1:\nNext first\n\nAD 2:\nNext second", received.get());
    }

    @Test
    void propagatesTheOriginalModelFailure() throws Exception {
        var failure = new IllegalStateException("Model unavailable");
        var comparator = comparator(p -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> comparator.areDuplicateDescriptions("a", "b")));
    }

    @Test
    void rejectsAnythingExceptTheTwoExactAnswers() throws Exception {
        for (String response : List.of("", " \n", "NO SUBSTANTIVE DIFFERENCES.",
                "no substantive differences", "SUBSTANTIVE  DIFFERENCES",
                "NO SUBSTANTIVE DIFFERENCES\nExplanation", "```\nSUBSTANTIVE DIFFERENCES\n```",
                "{\"duplicate\":true}", "DUPLICATE", "INSUFFICIENT INFORMATION")) {
            var comparator = comparator(p -> response);
            assertThrows(IllegalStateException.class,
                    () -> comparator.areDuplicateDescriptions("a", "b"), response);
        }
    }
}
