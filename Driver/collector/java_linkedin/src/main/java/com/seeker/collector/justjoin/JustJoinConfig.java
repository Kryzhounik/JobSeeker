package com.seeker.collector.justjoin;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Same search settings and API parameters as the existing Python HTTP fetcher. */
public record JustJoinConfig(String mainTech, String location, String publishedDays,
                             List<String> languages, List<String> experienceLevels,
                             String orderBy, String sortBy, int itemsCount, int limit, double delaySeconds) {
    public JustJoinConfig {
        languages = List.copyOf(languages);
        experienceLevels = List.copyOf(experienceLevels);
        if (mainTech.isBlank() || itemsCount <= 0 || limit < 0 || delaySeconds < 0)
            throw new IllegalArgumentException("Invalid JustJoin search settings");
    }

    public static JustJoinConfig load(Path projectRoot) throws IOException {
        Properties p = new Properties();
        for (String file : List.of("Driver/collector/config/justjoin.properties",
                "Data/Profile/Driver/collector/justjoin.properties")) {
            try (var reader = Files.newBufferedReader(projectRoot.resolve(file), StandardCharsets.UTF_8)) {
                p.load(reader);
            }
        }
        return new JustJoinConfig(p.getProperty("mainTech", "").trim(), p.getProperty("location", "all-locations"),
                p.getProperty("publishedDays", ""), csv(p.getProperty("languages", "")),
                csv(p.getProperty("experienceLevels", "")), p.getProperty("orderBy", "DESC"),
                p.getProperty("sortBy", "published"), Integer.parseInt(p.getProperty("itemsCount", "100")),
                Integer.parseInt(p.getProperty("limit", "0")), Double.parseDouble(p.getProperty("delaySeconds", "15")));
    }

    String query() {
        var parts = new ArrayList<String>();
        add(parts, "categories", mainTech);
        add(parts, "orderBy", "ASC".equals(orderBy) ? "ascending" : "descending");
        add(parts, "sortBy", "published".equals(sortBy) ? "publishedAt" : sortBy);
        if (!publishedDays.isBlank()) add(parts, "publishedSinceDays", publishedDays);
        languages.forEach(value -> add(parts, "languages", value));
        experienceLevels.forEach(value -> add(parts, "experienceLevels", value));
        return String.join("&", parts);
    }

    String searchUrl() {
        return "https://justjoin.it/job-offers/" + location + "/" + mainTech;
    }

    private static void add(List<String> parts, String name, String value) {
        parts.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private static List<String> csv(String text) {
        return Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
