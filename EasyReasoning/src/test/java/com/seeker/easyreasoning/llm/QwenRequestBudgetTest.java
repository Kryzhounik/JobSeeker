package com.seeker.easyreasoning.llm;

import com.seeker.easyreasoning.RequestTooLargeException;
import com.seeker.easyreasoning.TestTokenizers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class QwenRequestBudgetTest {
    @TempDir
    Path tokenizerDirectory;

    private Path tokenizerFile() throws IOException {
        return TestTokenizers.write(tokenizerDirectory);
    }

    @Test
    void accountsForFullChatTemplateAndResponseReserveAtExactBoundary() throws IOException {
        String prompt = "AD 1: Java\n\nAD 2: Python";
        int request;
        int response;
        try (var oneTokenShort = new QwenRequestBudget(1, tokenizerFile())) {
            var error = assertThrows(RequestTooLargeException.class,
                    () -> oneTokenShort.validate(prompt));
            request = error.requestTokens();
            response = error.responseTokens();
            assertEquals(1, error.contextLimit());
        }
        assertTrue(request > 20, "System, user and assistant ChatML markers must be counted");
        assertTrue(response > 1, "Both verdict alternatives and the EOS token are reserved");
        try (var overflow = new QwenRequestBudget(request + response - 1, tokenizerFile())) {
            var error = assertThrows(RequestTooLargeException.class, () -> overflow.validate(prompt));
            assertEquals(request, error.requestTokens());
            assertEquals(request + response - 1, error.contextLimit());
            assertEquals(response, error.responseTokens());
        }
        try (var exact = new QwenRequestBudget(request + response, tokenizerFile())) {
            assertEquals(response, exact.validate(prompt));
        }
    }

    @Test
    void headersAndUnicodePromptContributeToTokenizedRequest() throws IOException {
        String shortPrompt = "漢";
        String longPrompt = "漢".repeat(80);
        try (var budget = new QwenRequestBudget(1, tokenizerFile())) {
            var shortSize = assertThrows(RequestTooLargeException.class, () -> budget.validate(shortPrompt));
            var longSize = assertThrows(RequestTooLargeException.class, () -> budget.validate(longPrompt));
            assertTrue(longSize.requestTokens() > shortSize.requestTokens());
            assertEquals(shortSize.responseTokens(), longSize.responseTokens());
        }
    }
    @Test
    void characterLengthAndTokenLengthCanHaveOppositeOrdering() throws IOException {
        String ascii = "a".repeat(200);
        String han = "漢".repeat(50);
        try (var budget = new QwenRequestBudget(1, tokenizerFile())) {
            int asciiTokens = assertThrows(RequestTooLargeException.class,
                    () -> budget.validate(ascii)).requestTokens();
            int hanTokens = assertThrows(RequestTooLargeException.class,
                    () -> budget.validate(han)).requestTokens();
            assertTrue(ascii.length() > han.length());
            assertTrue(asciiTokens < hanTokens);
        }
    }

    @Test
    void missingTokenizerFailsOnlyWhenValidationNeedsItAndNamesThePath() throws IOException {
        Path missing = tokenizerDirectory.resolve("absent/tokenizer.json");
        try (var budget = new QwenRequestBudget(10000, missing)) {
            assertFalse(Files.exists(missing));
            var error = assertThrows(UncheckedIOException.class, () -> budget.validate("prompt"));
            assertTrue(error.getMessage().contains(missing.toAbsolutePath().normalize().toString()));
        }
    }

    @Test
    void separateTokenizerFilesLoadIndependently() throws IOException {
        Path first = TestTokenizers.write(tokenizerDirectory.resolve("first"));
        Path second = TestTokenizers.write(tokenizerDirectory.resolve("second"));
        try (var firstBudget = new QwenRequestBudget(1, first);
             var secondBudget = new QwenRequestBudget(1, second)) {
            int firstCount = assertThrows(RequestTooLargeException.class,
                    () -> firstBudget.validate("a".repeat(200))).requestTokens();
            int secondCount = assertThrows(RequestTooLargeException.class,
                    () -> secondBudget.validate("漢".repeat(50))).requestTokens();
            assertTrue(firstCount < secondCount);
        }
    }

}
