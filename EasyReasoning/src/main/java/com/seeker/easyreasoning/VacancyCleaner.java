package com.seeker.easyreasoning;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Optional boundary detection over the common generation API. */
public final class VacancyCleaner {
    private static final Pattern NEWLINE = Pattern.compile("\r\n|\r|\n");
    private static final Pattern JSON_BLOCK = Pattern.compile("(?s)^```(?:json)?\\s*\\n(.*)\\n```$");
    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private final boolean enabled;
    private final EasyReasoningService service;
    private final Path diagnostics;
    private final Path promptFile;

    public VacancyCleaner(boolean enabled, EasyReasoningService service, Path diagnostics, Path promptFile) {
        this.enabled = enabled;
        this.service = service;
        this.diagnostics = diagnostics;
        this.promptFile = promptFile;
    }

    public String cleanVacancy(String text) {
        Objects.requireNonNull(text, "text");
        if (!enabled || text.isEmpty()) return text;

        var lines = new ArrayList<Line>();
        var matcher = NEWLINE.matcher(text);
        int start = 0;
        while (matcher.find()) {
            lines.add(new Line(start, matcher.end()));
            start = matcher.end();
        }
        if (start < text.length()) lines.add(new Line(start, text.length()));
        StringBuilder prompt;
        try {
            prompt = new StringBuilder(Files.readString(promptFile, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot read vacancy trimming prompt: " + promptFile, failure);
        }
        if (!prompt.isEmpty() && prompt.charAt(prompt.length() - 1) != '\n') prompt.append('\n');
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            prompt.append(index + 1).append(": ").append(text, line.start(), line.end());
            if (index == lines.size() - 1 && !NEWLINE.matcher(text.substring(line.start())).find()) {
                prompt.append('\n');
            }
        }

        String id = UUID.randomUUID().toString();
        save(id + ".before.txt", text);
        String response = service.generate(prompt.toString());
        save(id + ".response.txt", response);
        var block = JSON_BLOCK.matcher(response.strip());
        var bounds = JSON.readTree(block.matches() ? block.group(1) : response);
        if (bounds == null || !bounds.isObject()
                || !bounds.has("from_line") || !bounds.has("to_line")
                || !bounds.get("from_line").isIntegralNumber() || !bounds.get("to_line").isIntegralNumber()
                || !bounds.get("from_line").canConvertToInt() || !bounds.get("to_line").canConvertToInt()) {
            throw new IllegalStateException("Expected vacancy boundaries: integer from_line and to_line");
        }
        int from = bounds.get("from_line").asInt();
        int to = bounds.get("to_line").asInt();
        if (from < 1 || to < from || to > lines.size()) {
            throw new IllegalStateException("Vacancy boundaries outside source lines: " + from + ".." + to);
        }
        String cleaned = text.substring(lines.get(from - 1).start(), lines.get(to - 1).end());
        save(id + ".after.txt", cleaned);
        return cleaned;
    }

    private void save(String file, String text) {
        try {
            Files.createDirectories(diagnostics);
            Files.writeString(diagnostics.resolve(file), text, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Cannot save vacancy trimming diagnostics", failure);
        }
    }

    private record Line(int start, int end) {}
}
