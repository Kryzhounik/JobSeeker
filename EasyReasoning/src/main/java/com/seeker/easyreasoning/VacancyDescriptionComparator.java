package com.seeker.easyreasoning;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Compares description content through the shared generation gateway. */
final class VacancyDescriptionComparator {
    private final EasyReasoningService service;
    private final Path promptFile;

    VacancyDescriptionComparator(EasyReasoningService service, Path promptFile) {
        this.service = service;
        this.promptFile = promptFile;
    }

    boolean areDuplicateDescriptions(String firstText, String secondText) {
        Objects.requireNonNull(firstText, "firstText");
        Objects.requireNonNull(secondText, "secondText");
        String instruction;
        try {
            instruction = Files.readString(promptFile, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read vacancy comparison prompt: " + promptFile, failure);
        }
        String response = service.generate(instruction + "\n\nAD 1:\n" + firstText
                + "\n\nAD 2:\n" + secondText).strip();
        return switch (response) {
            case "NO SUBSTANTIVE DIFFERENCES" -> true;
            case "SUBSTANTIVE DIFFERENCES" -> false;
            default -> throw new IllegalStateException("Unexpected vacancy comparison response: " + response);
        };
    }
}
