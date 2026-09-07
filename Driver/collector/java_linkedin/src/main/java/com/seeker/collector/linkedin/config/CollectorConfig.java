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
        int pageTimeoutSeconds,
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
                integer(properties, "pageTimeoutSeconds", 30),
                integer(properties, "detailsTimeoutSeconds", 20)
        );
    }

    public CollectorConfig withLimit(Integer limitOverride) {
        int resolvedLimit = limitOverride == null ? limit : limitOverride;
        if (resolvedLimit <= 0) {
            throw new IllegalArgumentException("LinkedIn collector limit must be positive");
        }
        return new CollectorConfig(
                keywords,
                locations,
                experience,
                remoteBroadWorkplace,
                workplace,
                jobTypes,
                datePosted,
                sort,
                resolvedLimit,
                delaySeconds,
                browserChannel,
                pageTimeoutSeconds,
                detailsTimeoutSeconds
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
