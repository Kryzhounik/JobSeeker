package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.function.Consumer;

final class CollectorRunner {
    private static final String PROGRESS_PREFIX = "COLLECTOR_PROGRESS ";

    record Progress(String runId, String stage, String location, String search,
                    int page, int cards, int accepted, int limit,
                    Map<String, Integer> outcomes, String message) { }

    record Result(String runId, String status, int accepted,
                  Map<String, Integer> outcomes, String message) { }

    private final Path projectRoot;
    private final ObjectMapper json = new ObjectMapper();

    CollectorRunner(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    Result run(Consumer<Progress> updateProgress) throws Exception {
        Path runtime = projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties");
        Path config = projectRoot.resolve("Driver/collector/config/linkedin.properties");
        String javaExecutable = readProperty(runtime, "java.executable");
        int limit = Integer.parseInt(readProperty(config, "limit"));
        if (limit <= 0) throw new IllegalArgumentException("Collector limit must be positive.");
        updateProgress.accept(new Progress("", "starting", "", "", 0, 0, 0,
                limit, Map.of(), ""));
        Path javaPath = Path.of(javaExecutable);
        Path javaw = javaPath.resolveSibling("javaw.exe");
        if (Files.isRegularFile(javaw)) javaPath = javaw;
        Path jar = projectRoot.resolve("Driver/collector/java_linkedin/linkedin-collector.jar");
        if (!Files.isRegularFile(javaPath)) throw new IOException("Java not found: " + javaPath);
        if (!Files.isRegularFile(jar)) throw new IOException("Collector jar not found: " + jar);

        Process process = new ProcessBuilder(javaPath.toString(), "-jar", jar.toString(), "batch")
                .directory(projectRoot.toFile()).redirectErrorStream(true).start();
        List<String> tail = new ArrayList<>();
        String finalJson = "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(PROGRESS_PREFIX)) {
                    updateProgress.accept(parseProgress(line));
                    continue;
                }
                tail.add(line);
                if (tail.size() > 40) tail.removeFirst();
                if (line.stripLeading().startsWith("{")) finalJson = line;
            }
        } catch (IOException error) {
            process.destroyForcibly();
            throw error;
        }
        int exitCode;
        try {
            exitCode = process.waitFor();
        } catch (InterruptedException error) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw error;
        }
        if (exitCode != 0) {
            throw new IOException("Java exited " + exitCode + ": " + String.join("\n", tail));
        }
        return parseResult(finalJson);
    }

    Progress parseProgress(String line) throws IOException {
        JsonNode value = json.readTree(line.substring(PROGRESS_PREFIX.length()));
        if (value == null || !value.isObject() || !value.hasNonNull("run_id")
                || !value.hasNonNull("stage")) {
            throw new IOException("Collector returned an invalid progress message.");
        }
        return new Progress(value.path("run_id").asText(), value.path("stage").asText(),
                value.path("location").asText(), value.path("search").asText(),
                value.path("page").asInt(), value.path("cards").asInt(),
                value.path("accepted_count").asInt(), value.path("limit").asInt(),
                counts(value.path("outcomes")), value.path("message").asText());
    }

    Result parseResult(String text) throws IOException {
        JsonNode value = json.readTree(text);
        if (value == null || !value.isObject() || !value.hasNonNull("run_id")
                || !value.hasNonNull("status") || !value.hasNonNull("accepted_count")) {
            throw new IOException("Collector did not return a complete result.");
        }
        return new Result(value.path("run_id").asText(), value.path("status").asText(),
                value.path("accepted_count").asInt(), counts(value.path("outcomes")),
                value.path("message").asText());
    }

    private static Map<String, Integer> counts(JsonNode value) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        value.fields().forEachRemaining(entry -> counts.put(entry.getKey(), entry.getValue().asInt()));
        return Map.copyOf(counts);
    }

    private static String readProperty(Path path, String key) throws IOException {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String item = line.strip();
            if (item.startsWith(key + "=")) return item.substring(key.length() + 1).strip();
        }
        throw new IOException("Missing " + key + " in " + path);
    }
}
