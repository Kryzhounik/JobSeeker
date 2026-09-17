package com.seeker.collector.linkedin.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public record CollectorConfig(
        String keywords,
        List<String> locations,
        List<String> experience,
        List<String> remoteBroadWorkplace,
        List<String> workplace,
        List<String> jobTypes,
        String datePosted,
        String sort,
        int limit,
        double delaySeconds,
        String browserChannel,
        int browserLaunchTimeoutSeconds,
        int pageTimeoutSeconds,
        int navigationRetries,
        int detailsTimeoutSeconds
) {
    public static CollectorConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        int limit = integer(properties, "limit", 100);
        if (limit <= 0) {
            throw new IllegalArgumentException("LinkedIn collector limit must be positive");
        }
        int browserLaunchTimeoutSeconds = integer(
                properties,
                "browserLaunchTimeoutSeconds",
                90
        );
        int pageTimeoutSeconds = integer(properties, "pageTimeoutSeconds", 60);
        int navigationRetries = integer(properties, "navigationRetries", 3);
        if (browserLaunchTimeoutSeconds <= 0 || pageTimeoutSeconds <= 0) {
            throw new IllegalArgumentException("LinkedIn timeouts must be positive");
        }
        if (navigationRetries < 0) {
            throw new IllegalArgumentException("LinkedIn navigation retries cannot be negative");
        }
        return new CollectorConfig(
                properties.getProperty("keywords", "").trim(),
                csv(properties.getProperty("locations", "")),
                csv(properties.getProperty("experience", "")),
                csv(properties.getProperty("remoteBroadWorkplace", "remote")),
                csv(properties.getProperty("workplace", "")),
                csv(properties.getProperty("jobTypes", "")),
                properties.getProperty("datePosted", "").trim(),
                properties.getProperty("sort", "").trim(),
                limit,
                decimal(properties, "delaySeconds", 15.0),
                properties.getProperty("browserChannel", "chrome").trim(),
                browserLaunchTimeoutSeconds,
                pageTimeoutSeconds,
                navigationRetries,
                integer(properties, "detailsTimeoutSeconds", 20)
        );
    }

    private static List<String> csv(String value) {
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
    }

    private static double decimal(Properties properties, String key, double fallback) {
        return Double.parseDouble(properties.getProperty(key, String.valueOf(fallback)).trim());
    }
}
