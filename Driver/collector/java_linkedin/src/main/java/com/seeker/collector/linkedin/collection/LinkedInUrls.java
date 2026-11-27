package com.seeker.collector.linkedin.collection;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class LinkedInUrls {
    private static final List<String> SEARCH_QUERY_KEYS = List.of(
            "keywords",
            "location",
            "geoId",
            "f_SAL",
            "f_TPR",
            "f_C",
            "start"
    );

    private LinkedInUrls() {
    }

    static void verifyQuery(String requestedUrl, String actualUrl) {
        String mismatch = searchQueryMismatch(requestedUrl, actualUrl);
        if (!mismatch.isBlank()) {
            throw new CollectionBlockedException(
                    "LinkedIn search URL does not match the requested query: "
                            + mismatch + "; actual URL is " + actualUrl
            );
        }
    }

    static String searchQueryMismatch(String requestedUrl, String actualUrl) {
        Map<String, String> expected = queryParameters(requestedUrl);
        Map<String, String> actual = queryParameters(actualUrl);
        for (String key : SEARCH_QUERY_KEYS) {
            String expectedValue = normalizedQueryValue(key, expected.get(key));
            String actualValue = normalizedQueryValue(key, actual.get(key));
            if (!Objects.equals(expectedValue, actualValue)) {
                return key + " expected=" + expectedValue + " actual=" + actualValue;
            }
        }
        return "";
    }

    static String canonicalUrl(String jobId) {
        return "https://www.linkedin.com/jobs/view/" + jobId + "/";
    }

    static String jobId(String url) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("/jobs/view/(\\d+)")
                .matcher(url);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a canonical LinkedIn job URL: " + url);
        }
        return matcher.group(1);
    }

    private static String normalizedQueryValue(String key, String value) {
        if ("start".equals(key) && (value == null || value.isBlank())) {
            return "0";
        }
        if (value != null && "f_SAL".equals(key)) {
            return Arrays.stream(value.split("\\$"))
                    .map(group -> {
                        String[] parts = group.split(":", 2);
                        return parts[0] + (parts.length == 1 ? "" : ":" + sortedValues(parts[1]));
                    }).sorted().collect(java.util.stream.Collectors.joining("$"));
        }
        if (value != null && "f_C".equals(key)) return sortedValues(value);
        return value;
    }

    private static String sortedValues(String value) {
        return Arrays.stream(value.split(",")).distinct().sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }

    private static Map<String, String> queryParameters(String url) {
        Map<String, String> result = new HashMap<>();
        String query = URI.create(url).getRawQuery();
        if (query == null || query.isBlank()) {
            return result;
        }
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(
                    URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    parts.length == 1
                            ? ""
                            : URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
            );
        }
        return result;
    }
}
