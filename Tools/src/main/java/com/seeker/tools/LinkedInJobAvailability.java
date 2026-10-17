package com.seeker.tools;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Pattern;

/** Shared HTTP request and page classification used by Check LinkedIn and ChatEvaluator. */
public final class LinkedInJobAvailability {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private LinkedInJobAvailability() { }

    public static HttpResponse<String> fetch(String vacancyUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(vacancyUrl))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/126.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml")
                .GET().build();
        return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    public static String classifyResponse(int code, URI url, String contentType, String body) {
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
}
