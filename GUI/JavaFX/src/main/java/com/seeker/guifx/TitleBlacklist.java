package com.seeker.guifx;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

final class TitleBlacklist {
    private final Path path;

    TitleBlacklist(Path projectRoot) {
        path = projectRoot.resolve(
                "Driver/collector/filtering/linkedin_preview_blocked_titles.txt");
    }

    boolean add(String value) throws IOException {
        String term = String.join(" ", Arrays.stream(value.strip().split("\\s+"))
                .filter(part -> !part.isBlank()).toList());
        if (term.isEmpty()) throw new IllegalArgumentException("Select a non-empty title fragment.");
        String existing = Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8) : "";
        boolean duplicate = existing.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .anyMatch(line -> line.equalsIgnoreCase(term));
        if (duplicate) return false;
        Files.createDirectories(path.getParent());
        String separator = existing.isEmpty() || existing.endsWith("\n") || existing.endsWith("\r")
                ? "" : System.lineSeparator();
        Files.writeString(path, separator + term + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return true;
    }

    void openInNotepad() throws IOException {
        if (!Files.isRegularFile(path)) throw new IOException("File not found: " + path);
        new ProcessBuilder("notepad.exe", path.toString()).start();
    }
}
