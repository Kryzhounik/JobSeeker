package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class LinkedInAvailabilityChecker {
    private static final Pattern JOB_ID = Pattern.compile(
            "(?:jobs/view|jobPosting)/(\\d+)", Pattern.CASE_INSENSITIVE);

    private final ClientData clientData;
    private final Path log;
    private final String closedStatus;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20)).build();

    LinkedInAvailabilityChecker(ClientData clientData, Path projectRoot, String closedStatus) {
        this.clientData = clientData;
        this.log = projectRoot.resolve("GUI/JavaFX/linkedin_availability_check.log");
        this.closedStatus = closedStatus;
    }

    Result run(Consumer<String> updateMessage) throws Exception {
        JsonNode candidates = clientData.loadLinkedInAvailabilityCandidates();
        if (closedStatus.isBlank()) {
            throw new IllegalStateException("Closed status is unavailable.");
        }
        int total = candidates.size();
        int processed = 0, closed = 0, skipped = 0, requests = 0;
        String error = "";
        for (JsonNode candidate : candidates) {
            String sourceUrl = text(candidate, "source_url");
            var idMatch = JOB_ID.matcher(sourceUrl);
            if (!idMatch.find()) {
                skipped++;
                processed++;
                appendLog("skip", Map.of("reason", "no_linkedin_id", "source_url", sourceUrl));
                updateMessage.accept(progress(processed, total, closed, skipped));
                continue;
            }
            String id = idMatch.group(1);
            if (requests > 0) Thread.sleep(5000);
            requests++;
            appendLog("request", Map.of("job_id", id, "source_url", sourceUrl));
            updateMessage.accept("LinkedIn check " + processed + "/" + total
                    + "; closed " + closed + "; skipped " + skipped + "; checking " + id);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/" + id))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET().build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception networkError) {
                error = id + ": Network error: " + networkError.getMessage();
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            }
            processed++;
            int code = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            appendLog("response", Map.of("job_id", id, "http_status", code,
                    "content_type", contentType, "body_length", body.length()));
            if (code == 404) {
                skipped++;
                appendLog("skip", Map.of("job_id", id, "reason", "http_404"));
            } else if (code != 200) {
                error = code == 429 ? "LinkedIn returned 429; stopped to avoid rate limiting."
                        : "LinkedIn returned HTTP " + code + ".";
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            } else if (body.toLowerCase(Locale.ROOT).contains("no longer accepting applications")) {
                closed += clientData.markJobsClosed(List.of(sourceUrl), closedStatus);
                appendLog("closed", Map.of("job_id", id, "updated", closed));
            } else if ((!contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).contains("html"))
                    || body.length() < 1000
                    || !body.toLowerCase(Locale.ROOT).contains("linkedin")
                    || !body.toLowerCase(Locale.ROOT).contains("job")) {
                error = "Unexpected HTML from LinkedIn for " + id + ".";
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            } else {
                appendLog("available", Map.of("job_id", id));
            }
            updateMessage.accept(progress(processed, total, closed, skipped));
        }
        String finalMessage = progress(processed, total, closed, skipped);
        appendLog("done", Map.of("processed", processed, "total", total,
                "closed", closed, "skipped", skipped, "error", error));
        return new Result(finalMessage, error);
    }

    private static String text(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String progress(int processed, int total, int closed, int skipped) {
        return "LinkedIn check " + processed + "/" + total
                + "; closed " + closed + "; skipped " + skipped;
    }

    private void appendLog(String event, Map<String, ?> fields) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("ts", Instant.now().toString());
            row.put("event", event);
            row.putAll(fields);
            Files.writeString(log, json.writeValueAsString(row) + System.lineSeparator(),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) { }
    }

    record Result(String message, String error) { }
}
