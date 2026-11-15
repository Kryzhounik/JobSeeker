package com.seeker.collector.linkedin.config;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Properties;
import static com.seeker.collector.common.PropertySettings.csv;

/** Effective search snapshot plus independently loaded LinkedIn technical settings. */
public record CollectorConfig(CollectionSettings.LinkedIn search, double delaySeconds, String browserChannel,
                              int browserLaunchTimeoutSeconds, int pageTimeoutSeconds, int navigationRetries,
                              int detailsTimeoutSeconds) {
    public CollectorConfig {
        Objects.requireNonNull(search);
        if (browserLaunchTimeoutSeconds <= 0 || pageTimeoutSeconds <= 0)
            throw new IllegalArgumentException("LinkedIn timeouts must be positive");
        if (navigationRetries < 0) throw new IllegalArgumentException("LinkedIn navigation retries cannot be negative");
    }

    public static CollectorConfig load(Path path) throws IOException { return load(path, profile(path)); }

    public static CollectorConfig load(Path path, Path profile) throws IOException {
        return load(path, loadSearch(path, profile));
    }

    public static CollectionSettings.LinkedIn loadSearch(Path path) throws IOException {
        return loadSearch(path, profile(path));
    }

    private static CollectionSettings.LinkedIn loadSearch(Path path, Path profile) throws IOException {
        Properties p = PropertySettings.load(path, profile);
        return new CollectionSettings.LinkedIn(p.getProperty("keywords", ""), csv(p.getProperty("locations", "")),
                csv(p.getProperty("experience", "")), csv(p.getProperty("remoteBroadWorkplace", "remote")),
                csv(p.getProperty("workplace", "")), csv(p.getProperty("jobTypes", "")),
                p.getProperty("datePosted", ""), p.getProperty("sort", ""), integer(p, "limit", 100));
    }

    /** Explicit search values never load personal preferences. */
    public static CollectorConfig load(Path path, CollectionSettings.LinkedIn search) throws IOException {
        Properties p = PropertySettings.load(path);
        return new CollectorConfig(search, Double.parseDouble(p.getProperty("delaySeconds", "15").trim()),
                p.getProperty("browserChannel", "chrome").trim(), integer(p, "browserLaunchTimeoutSeconds", 90),
                integer(p, "pageTimeoutSeconds", 60), integer(p, "navigationRetries", 3), integer(p, "detailsTimeoutSeconds", 20));
    }

    private static Path profile(Path path) {
        return path.toAbsolutePath().getParent().getParent().getParent().getParent()
                .resolve("Data/Profile/Driver/collector/linkedin.properties");
    }

    private static int integer(Properties properties, String key, int fallback) {
        return Integer.parseInt(properties.getProperty(key, String.valueOf(fallback)).trim());
    }
}
