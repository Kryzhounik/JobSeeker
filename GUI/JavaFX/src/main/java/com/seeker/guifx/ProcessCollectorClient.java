package com.seeker.guifx;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;
import com.seeker.collector.linkedin.support.JsonSupport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;

final class ProcessCollectorClient implements CollectorClient {
    private static final String PROGRESS_PREFIX = "COLLECTOR_PROGRESS ";

    private final Path projectRoot;

    ProcessCollectorClient(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    @Override
    public CollectionReport collect(Consumer<CollectionProgress> updateProgress) throws Exception {
        Path runtime = projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties");
        Path config = projectRoot.resolve("Driver/collector/config/linkedin.properties");
        String javaExecutable = readProperty(runtime, "java.executable");
        int limit = Integer.parseInt(readProperty(config, "limit"));
        if (limit <= 0) throw new IllegalArgumentException("Collector limit must be positive.");
        updateProgress.accept(new CollectionProgress("", "starting", "", "", 0, 0, 0,
                limit, Map.of(), ""));
        Path javaPath = Path.of(javaExecutable);
        Path javaw = javaPath.resolveSibling("javaw.exe");
        if (Files.isRegularFile(javaw)) javaPath = javaw;
        Path jar = projectRoot.resolve("Driver/collector/java_linkedin/linkedin-collector.jar");
        if (!Files.isRegularFile(javaPath)) throw new IOException("Java not found: " + javaPath);
        if (!Files.isRegularFile(jar)) throw new IOException("Collector jar not found: " + jar);

        Process process = new ProcessBuilder(javaPath.toString(), "-jar", jar.toString(), "collect")
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
        } catch (IOException | RuntimeException | Error error) {
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

    CollectionProgress parseProgress(String line) throws IOException {
        if (!line.startsWith(PROGRESS_PREFIX)) throw new IOException("Invalid progress prefix.");
        return parse(line.substring(PROGRESS_PREFIX.length()), CollectionProgress.class,
                "run_id", "stage");
    }

    CollectionReport parseResult(String text) throws IOException {
        return parse(text, CollectionReport.class, "source", "run_id", "status", "accepted_count");
    }

    private static <T> T parse(String text, Class<T> type, String... required) throws IOException {
        try {
            JsonObject value = JsonParser.parseString(text).getAsJsonObject();
            for (String key : required) {
                if (!value.has(key) || value.get(key).isJsonNull()) {
                    throw new IOException("Collector response is missing " + key);
                }
            }
            return JsonSupport.GSON.fromJson(value, type);
        } catch (JsonParseException | IllegalStateException error) {
            throw new IOException("Collector returned invalid JSON.", error);
        }
    }

    private static String readProperty(Path path, String key) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        String value = properties.getProperty(key, "").strip();
        if (value.isEmpty()) throw new IOException("Missing " + key + " in " + path);
        return value;
    }
}
