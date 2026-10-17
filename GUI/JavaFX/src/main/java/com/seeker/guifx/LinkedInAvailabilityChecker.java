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
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class LinkedInAvailabilityChecker {
    private final ClientData clientData;
    private final Path log;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    LinkedInAvailabilityChecker(ClientData clientData, Path projectRoot) {
        this.clientData = clientData;
        this.log = projectRoot.resolve("GUI/JavaFX/linkedin_availability_check.log");
    }

    Result run(Consumer<String> updateMessage) throws Exception {
        JsonNode candidates = clientData.loadLinkedInAvailabilityCandidates();
        int total = candidates.size();
        int processed = 0, unavailable = 0, skipped = 0, requests = 0;
        String error = "";
        for (JsonNode candidate : candidates) {
            String sourceUrl = text(candidate, "source_url");
            String id = text(candidate, "source_job_id");
            if (!id.matches("\\d+")) {
                skipped++;
                processed++;
                appendLog("skip", Map.of("reason", "no_linkedin_id", "source_url", sourceUrl));
                updateMessage.accept(progress(processed, total, unavailable, skipped));
                continue;
            }
            if (requests > 0) Thread.sleep(5000);
            requests++;
            appendLog("request", Map.of("job_id", id, "source_url", sourceUrl));
            updateMessage.accept("LinkedIn check " + processed + "/" + total
                    + "; unavailable " + unavailable + "; skipped " + skipped + "; checking " + id);
            HttpRequest request = HttpRequest.newBuilder(URI.create(
                            "https://www.linkedin.com/jobs/view/" + id + "/"))
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
            String outcome;
            try {
                outcome = classifyResponse(code, response.uri(), contentType, body);
            } catch (IllegalStateException invalidResponse) {
                error = id + ": " + invalidResponse.getMessage();
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            }
            JsonNode state = clientData.recordLinkedInAvailability(id, outcome);
            if (!outcome.equals("available")) unavailable++;
            appendLog(state.path("availability").asText(), Map.of("job_id", id,
                    "unavailable_checks", state.path("unavailable_checks").asInt()));
            updateMessage.accept(progress(processed, total, unavailable, skipped));
        }
        String finalMessage = progress(processed, total, unavailable, skipped);
        appendLog("done", Map.of("processed", processed, "total", total,
                "unavailable", unavailable, "skipped", skipped, "error", error));
        return new Result(finalMessage, error);
    }

    static String classifyResponse(int code, URI url, String contentType, String body) {
        String path = url.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("/login") || path.contains("/authwall") || path.contains("/checkpoint")) {
            throw new IllegalStateException("LinkedIn requires authentication.");
        }
        if (code == 404) return "unavailable";
        if (code != 200) {
            throw new IllegalStateException(code == 429
                    ? "LinkedIn returned 429; stopped to avoid rate limiting."
                    : "LinkedIn returned HTTP " + code + ".");
        }
        String html = body.toLowerCase(Locale.ROOT);
        if (html.contains("no longer accepting applications")
                || html.contains("this job is no longer available")) return "closed";
        boolean description = html.contains("show-more-less-html__markup")
                || html.contains("description__text")
                || Pattern.compile("\"@type\"\\s*:\\s*\"jobposting\"").matcher(html).find();
        if ((!contentType.isBlank() && !contentType.toLowerCase(Locale.ROOT).contains("html"))
                || body.length() < 1000 || !path.contains("/jobs/view/")
                || !html.contains("linkedin") || !description) {
            throw new IllegalStateException("Unexpected LinkedIn page; availability was not changed.");
        }
        return "available";
    }

    private static String text(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static String progress(int processed, int total, int unavailable, int skipped) {
        return "LinkedIn check " + processed + "/" + total
                + "; unavailable " + unavailable + "; skipped " + skipped;
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
