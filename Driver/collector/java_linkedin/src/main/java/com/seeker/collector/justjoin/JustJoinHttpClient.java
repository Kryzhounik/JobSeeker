package com.seeker.collector.justjoin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

/** HTTP acquisition only; filtering and storage belong to the shared processor. */
public final class JustJoinHttpClient {
    private final HttpClient http;
    private final URI api;
    private final String referer;
    private final long delayMillis;
    private boolean requested;

    public JustJoinHttpClient(JustJoinConfig config) {
        this(config, URI.create("https://justjoin.it/api/candidate-api/"));
    }

    // Local HTTP fixture server uses exactly the production request path.
    JustJoinHttpClient(JustJoinConfig config, URI api) {
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
        this.api = api;
        this.referer = config.searchUrl();
        this.delayMillis = (long)(config.delaySeconds() * 1000);
    }

    JsonObject get(String path) throws IOException {
        URI uri = api.resolve(path);
        try {
            if (requested && delayMillis > 0) Thread.sleep(delayMillis);
            requested = true;
            var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Referer", referer).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
                throw new IOException("JustJoin HTTP " + response.statusCode() + ": " + uri);
            return JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("JustJoin request interrupted: " + uri, error);
        }
    }
}
