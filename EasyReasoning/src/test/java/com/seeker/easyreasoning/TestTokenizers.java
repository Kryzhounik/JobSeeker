package com.seeker.easyreasoning;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes the small, deterministic tokenizer used by offline tests. */
public final class TestTokenizers {
    private static final String FIXTURE = "/fixtures/tiny-tokenizer.json";

    private TestTokenizers() {}

    public static Path write(Path directory) throws IOException {
        Path file = directory.resolve("tokenizer.json");
        Files.createDirectories(directory);
        try (var input = TestTokenizers.class.getResourceAsStream(FIXTURE)) {
            if (input == null) throw new IOException("Missing test tokenizer fixture: " + FIXTURE);
            Files.copy(input, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }
}
