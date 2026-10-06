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
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class CollectorRunner {
    private static final Pattern PROGRESS = Pattern.compile("\\baccepted=(\\d+)/(\\d+)\\b");

    private final Path projectRoot;
    private final ObjectMapper json = new ObjectMapper();

    CollectorRunner(Path projectRoot) {
        this.projectRoot = projectRoot;
    }

    String run(Consumer<String> updateMessage) throws Exception {
        Path runtime = projectRoot.resolve("Driver/collector/java_linkedin/runtime.properties");
        Path config = projectRoot.resolve("Driver/collector/config/linkedin.properties");
        String javaExecutable = readProperty(runtime, "java.executable");
        int limit = Integer.parseInt(readProperty(config, "limit"));
        if (limit <= 0) throw new IllegalArgumentException("Collector limit must be positive.");
        Path javaPath = Path.of(javaExecutable);
        Path javaw = javaPath.resolveSibling("javaw.exe");
        if (Files.isRegularFile(javaw)) javaPath = javaw;
        Path jar = projectRoot.resolve("Driver/collector/java_linkedin/target/linkedin-collector.jar");
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
                tail.add(line);
                if (tail.size() > 40) tail.removeFirst();
                var matcher = PROGRESS.matcher(line);
                if (matcher.find()) {
                    updateMessage.accept("LinkedIn collector accepted "
                            + matcher.group(1) + "/" + matcher.group(2));
                }
                if (line.stripLeading().startsWith("{")) finalJson = line;
            }
        } catch (IOException error) {
            process.destroyForcibly();
            throw error;
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Java exited " + exitCode + ": " + String.join("\n", tail));
        }
        JsonNode result = json.readTree(finalJson);
        int accepted = result.path("accepted_count").asInt(-1);
        return accepted >= 0 ? "LinkedIn collector complete; accepted " + accepted
                : "LinkedIn collector complete";
    }

    private static String readProperty(Path path, String key) throws IOException {
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String item = line.strip();
            if (item.startsWith(key + "=")) return item.substring(key.length() + 1).strip();
        }
        throw new IOException("Missing " + key + " in " + path);
    }
}
