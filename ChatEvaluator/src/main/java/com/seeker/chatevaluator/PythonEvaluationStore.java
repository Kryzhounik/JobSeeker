package com.seeker.chatevaluator;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Short calls to the project's DB layer; no process or transaction spans evaluation. */
final class PythonEvaluationStore implements GptEvaluation.Store {
    private static final Gson JSON = new Gson();
    private final Path root;

    PythonEvaluationStore(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Integer findJobId(String url) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "find_job_id");
        request.addProperty("url", url);
        JsonElement result = call(request);
        return result.isJsonNull() ? null : result.getAsInt();
    }

    @Override
    public void save(int jobId, String answer, String evaluatedAt) throws Exception {
        JsonObject request = new JsonObject();
        request.addProperty("operation", "save");
        request.addProperty("job_id", jobId);
        request.addProperty("answer", answer);
        request.addProperty("evaluated_at", evaluatedAt);
        call(request);
    }

    private JsonElement call(JsonObject request) throws Exception {
        Properties runtime = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve(
                "Driver/collector/java_linkedin/runtime.properties"), StandardCharsets.UTF_8)) {
            runtime.load(reader);
        }
        ProcessBuilder builder = new ProcessBuilder(
                runtime.getProperty("python.executable", "python").strip(),
                root.resolve("Driver/db/gpt_evaluations.py").toString(),
                root.resolve("Data/jobs.sqlite").toString())
                .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT);
        builder.environment().put("PYTHONIOENCODING", "utf-8");
        Process process = builder.start();
        try {
            try (var input = process.getOutputStream()) {
                input.write(JSON.toJson(request).getBytes(StandardCharsets.UTF_8));
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            JsonObject response = JSON.fromJson(output, JsonObject.class);
            if (exitCode != 0 || response == null || response.has("error")) {
                String message = response != null && response.has("error")
                        ? response.get("error").getAsString() : "DB process exited with code " + exitCode;
                throw new IOException(message);
            }
            return response.get("result");
        } finally {
            if (process.isAlive()) process.destroy();
        }
    }
}
