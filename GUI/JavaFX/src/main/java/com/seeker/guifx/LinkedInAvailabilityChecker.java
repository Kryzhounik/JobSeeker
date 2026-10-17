package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seeker.tools.LinkedInJobAvailability;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

final class LinkedInAvailabilityChecker {
    private final ClientData clientData;
    private final Path log;
    private final ObjectMapper json = new ObjectMapper();

    LinkedInAvailabilityChecker(ClientData clientData, Path projectRoot) {
        this.clientData = clientData;
        this.log = projectRoot.resolve("GUI/JavaFX/linkedin_availability_check.log");
    }

    Result run(Consumer<String> updateMessage) throws Exception {
        long started = System.nanoTime();
        JsonNode candidates = clientData.loadLinkedInAvailabilityCandidates();
        int total = candidates.size();
        int processed = 0, unavailable = 0, skipped = 0, requests = 0;
        int available = 0, closed = 0, deleted = 0, rateLimits = 0;
        RequestPacing pacing = new RequestPacing();
        String error = "";
        appendLog("start", Map.of("total", total));
        for (int index = 0; index < total;) {
            JsonNode candidate = candidates.get(index);
            String sourceUrl = text(candidate, "source_url");
            String id = text(candidate, "source_job_id");
            if (!id.matches("\\d+")) {
                index++;
                skipped++;
                processed++;
                appendLog("skip", Map.of("reason", "no_linkedin_id", "source_url", sourceUrl));
                updateMessage.accept(progress(processed, total, unavailable, skipped));
                continue;
            }
            if (requests > 0) {
                updateMessage.accept(progress(processed, total, unavailable, skipped)
                        + "; waiting " + pacing.delayMillis() / 1000.0 + " s; next " + id);
                Thread.sleep(pacing.delayMillis());
            }
            requests++;
            appendLog("request", Map.of("job_id", id, "source_url", sourceUrl));
            updateMessage.accept("LinkedIn check " + processed + "/" + total
                    + "; unavailable " + unavailable + "; skipped " + skipped + "; checking " + id);
            HttpResponse<String> response;
            try {
                response = LinkedInJobAvailability.fetch("https://www.linkedin.com/jobs/view/" + id + "/");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw interrupted;
            } catch (Exception networkError) {
                error = id + ": Network error: " + networkError.getMessage();
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            }
            int code = response.statusCode();
            String body = response.body() == null ? "" : response.body();
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            appendLog("response", Map.of("job_id", id, "http_status", code,
                    "content_type", contentType, "body_length", body.length(),
                    "final_url", response.uri().toString(),
                    "retry_after", response.headers().firstValue("Retry-After").orElse("")));
            if (code == 429) {
                rateLimits++;
                if (pacing.onRateLimit()) {
                    appendLog("retry", Map.of("job_id", id, "delay_ms", pacing.delayMillis()));
                    continue;
                }
                error = id + ": LinkedIn returned 429 after a 120-second pause; check stopped.";
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            }
            String outcome;
            try {
                outcome = LinkedInJobAvailability.classifyResponse(code, response.uri(), contentType, body);
            } catch (IllegalStateException invalidResponse) {
                error = id + ": " + invalidResponse.getMessage();
                appendLog("stop_error", Map.of("job_id", id, "error", error));
                break;
            }
            JsonNode state = clientData.recordLinkedInAvailability(id, outcome);
            index++;
            processed++;
            long previousDelay = pacing.delayMillis();
            pacing.onSuccess();
            if (pacing.delayMillis() != previousDelay) {
                appendLog("speed_up", Map.of("delay_ms", pacing.delayMillis()));
            }
            if (!outcome.equals("available")) unavailable++;
            switch (state.path("availability").asText()) {
                case "available" -> available++;
                case "closed" -> closed++;
                case "deleted" -> deleted++;
            }
            appendLog(state.path("availability").asText(), Map.of("job_id", id,
                    "unavailable_checks", state.path("unavailable_checks").asInt()));
            updateMessage.accept(progress(processed, total, unavailable, skipped));
        }
        Result result = new Result(error, Duration.ofNanos(System.nanoTime() - started),
                total, available, unavailable - closed - deleted, closed, deleted,
                skipped, requests, rateLimits);
        appendLog("done", Map.of("processed", result.processed(), "total", total,
                "skipped", skipped, "requests", requests, "rate_limits", rateLimits,
                "elapsed_seconds", result.elapsed().toSeconds(), "error", error,
                "outcomes", Map.of("available", available, "unavailable", result.unavailable(),
                        "closed", closed, "deleted", deleted)));
        return result;
    }

    static final class RequestPacing {
        private long delayMillis = 5000;
        private int successes;

        long delayMillis() {
            return delayMillis;
        }

        boolean onRateLimit() {
            successes = 0;
            if (delayMillis == 120000) return false;
            delayMillis = Math.min(120000, delayMillis * 2);
            return true;
        }

        void onSuccess() {
            if (++successes == 10) {
                delayMillis = Math.max(5000, delayMillis / 2);
                successes = 0;
            }
        }
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

    record Result(String error, Duration elapsed, int total, int available, int unavailable,
                  int closed, int deleted, int skipped, int requests, int rateLimits) {
        int processed() {
            return available + unavailable + closed + deleted;
        }

        String message() {
            return progress(processed(), total, unavailable + closed + deleted, skipped);
        }

        String details() {
            return "Duration: %d min %02d s%nProcessed: %d / %d%nAvailable: %d%nUnavailable: %d%nClosed: %d%nDeleted: %d%nSkipped: %d%nHTTP requests: %d; HTTP 429: %d"
                    .formatted(elapsed.toMinutes(), elapsed.toSecondsPart(), processed(), total,
                            available, unavailable, closed, deleted, skipped, requests, rateLimits);
        }
    }
}
