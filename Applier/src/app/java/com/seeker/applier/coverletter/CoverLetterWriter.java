package com.seeker.applier.coverletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeker.codex.CodexClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import com.seeker.codex.UsageLimitExceededException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.time.Instant;

/** Returns a saved cover letter or generates one through the metered Proxy. */
public final class CoverLetterWriter {
    private final Path root;
    public CoverLetterWriter(Path root) { this.root = root.toAbsolutePath().normalize(); }
    public String generate(long jobId) throws IOException, InterruptedException, UsageLimitExceededException {
        if (jobId <= 0) throw new IllegalArgumentException("Expected a positive jobs.id");
        Properties runtime = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("Driver/collector/java_linkedin/runtime.properties"))) {
            runtime.load(reader);
        }
        String python = runtime.getProperty("python.executable", "python");
        Path database = root.resolve(runtime.getProperty("database.path", "Data/jobs.sqlite")).normalize();
        byte[] inputBytes = loadContext(python, database, jobId);
        ObjectMapper json = new ObjectMapper();
        var savedLetter = json.readTree(inputBytes).path("letter");
        if (savedLetter.isTextual()) return savedLetter.asText();
        Path input = Files.createTempFile("cover-letter-", ".json");
        try {
            Files.write(input, inputBytes);
            String response = invokeCodex(List.of(python, root.resolve("Driver/codex_proxy/metrics_proxy.py").toString(),
                    "--run-id", "cover-letter-" + Instant.now().toEpochMilli(),
                    "--operation", "cover_letter", "--target", Long.toString(jobId),
                    "--reasoning-effort", "xhigh",
                    "--db", database.toString(),
                    "--instruction", root.resolve("Applier/prompts/cover_letter_prompt.md").toString(),
                    "--input", input.toString(),
                    "--output-schema", root.resolve("Applier/cover_letter.schema.json").toString(),
                    "--context", root.resolve("Data/Profile/resume.txt").toString(),
                    "--context", root.resolve("Data/Profile/Applier/candidate_context.md").toString()));
            var text = json.readTree(response).path("letter");
            if (!text.isTextual() || text.asText().isBlank()) throw new IllegalStateException("Proxy returned no letter");
            saveLetter(python, database, jobId, text.asText());
            return text.asText();
        } finally { Files.deleteIfExists(input); }
    }
    byte[] loadContext(String python, Path database, long jobId) throws IOException, InterruptedException {
        Process lookup = new ProcessBuilder(python, root.resolve("Applier/cover_letter_job.py").toString(),
                database.toString(), Long.toString(jobId))
                .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        byte[] input = lookup.getInputStream().readAllBytes();
        if (lookup.waitFor() != 0) throw new IllegalArgumentException("Cannot load cover-letter context for jobs.id=" + jobId);
        return input;
    }
    String invokeCodex(List<String> command) throws IOException, InterruptedException, UsageLimitExceededException {
        return CodexClient.call(command, root);
    }
    void saveLetter(String python, Path database, long jobId, String letter) throws IOException, InterruptedException {
        Process save = new ProcessBuilder(python, root.resolve("Applier/cover_letter_job.py").toString(),
                database.toString(), Long.toString(jobId), "--save")
                .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        try (var stdin = save.getOutputStream()) {
            stdin.write(letter.getBytes(StandardCharsets.UTF_8));
        }
        if (save.waitFor() != 0) throw new IOException("Cannot save cover letter for jobs.id=" + jobId);
    }
    public Path generate(long jobId, Path output) throws IOException, InterruptedException, UsageLimitExceededException {
        String text = generate(jobId);
        Path path = root.resolve(output).normalize();
        Files.writeString(path, text, StandardCharsets.UTF_8);
        return path;
    }
}
