package com.seeker.collector.justjoin;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import static com.seeker.collector.common.PropertySettings.csv;

/** Effective search snapshot plus HTTP page size and delay. */
public record JustJoinConfig(CollectionSettings.JustJoin search, int itemsCount, double delaySeconds) {
    public JustJoinConfig {
        Objects.requireNonNull(search);
        if (itemsCount <= 0 || delaySeconds < 0) throw new IllegalArgumentException("Invalid JustJoin technical settings");
    }

    public static JustJoinConfig load(Path root) throws IOException { return load(root, loadSearch(root)); }

    public static CollectionSettings.JustJoin loadSearch(Path root) throws IOException {
        Properties p = PropertySettings.load(technical(root), root.resolve("Data/Profile/Driver/collector/justjoin.properties"));
        return new CollectionSettings.JustJoin(p.getProperty("mainTech", ""), p.getProperty("location", "all-locations"),
                p.getProperty("publishedDays", ""), csv(p.getProperty("languages", "")),
                csv(p.getProperty("experienceLevels", "")), p.getProperty("orderBy", "DESC"),
                p.getProperty("sortBy", "published"), Integer.parseInt(p.getProperty("limit", "0").trim()));
    }

    /** Explicit search values never load personal preferences. */
    public static JustJoinConfig load(Path root, CollectionSettings.JustJoin search) throws IOException {
        Properties p = PropertySettings.load(technical(root));
        return new JustJoinConfig(search, Integer.parseInt(p.getProperty("itemsCount", "100").trim()),
                Double.parseDouble(p.getProperty("delaySeconds", "15").trim()));
    }

    private static Path technical(Path root) { return root.resolve("Driver/collector/config/justjoin.properties"); }

    String query() {
        var parts = new ArrayList<String>();
        add(parts, "categories", search.mainTech());
        add(parts, "orderBy", "ASC".equals(search.orderBy()) ? "ascending" : "descending");
        add(parts, "sortBy", "published".equals(search.sortBy()) ? "publishedAt" : search.sortBy());
        if (!search.publishedDays().isBlank()) add(parts, "publishedSinceDays", search.publishedDays());
        search.languages().forEach(value -> add(parts, "languages", value));
        search.experienceLevels().forEach(value -> add(parts, "experienceLevels", value));
        return String.join("&", parts);
    }

    String searchUrl() { return "https://justjoin.it/job-offers/" + search.location() + "/" + search.mainTech(); }

    private static void add(List<String> parts, String name, String value) {
        parts.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }
}
