package com.seeker.collector.core;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import com.seeker.collector.common.ProjectPaths;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.justjoin.JustJoinConfig;

/** Fresh default user preferences, loaded only for selected sources. */
public final class CollectionSettingsLoader {
    private CollectionSettingsLoader() { }

    /** Includes valid disabled preferences; unavailable disabled sources remain null. */
    public static CollectionSettings loadForEditing(Path root) throws IOException {
        var sources = loadSources(root.resolve("Driver/collector"));
        return new CollectionSettings(sources,
                editable(sources.contains("linkedin"), () -> CollectorConfig.loadSearch(root.resolve("Driver/collector/config/linkedin.properties"))),
                editable(sources.contains("justjoin"), () -> JustJoinConfig.loadSearch(root)));
    }

    private interface SearchLoader<T> { T load() throws IOException; }

    private static <T> T editable(boolean enabled, SearchLoader<T> loader) throws IOException {
        try { return loader.load(); }
        catch (IOException | IllegalArgumentException error) {
            if (enabled) throw error;
            return null;
        }
    }

    /** Only enabled sources are saved; unrelated entries and disabled preferences survive. */
    public static void save(Path root, CollectionSettings settings) throws IOException {
        Path profile = root.resolve("Data/Profile/Driver/collector");
        if (settings.sources().contains("linkedin")) {
            var s = settings.linkedin();
            update(profile.resolve("linkedin.properties"), Map.of(
                    "keywords", String.join(",", s.keywords()), "locations", String.join(",", s.locations()),
                    "experience", String.join(",", s.experience()),
                    "remoteBroadWorkplace", String.join(",", s.remoteBroadWorkplace()),
                    "workplace", String.join(",", s.workplace()), "jobTypes", String.join(",", s.jobTypes()),
                    "datePosted", s.datePosted(), "limit", Integer.toString(s.limit()),
                    "includeRecommendations", Boolean.toString(s.includeRecommendations())));
        }
        if (settings.sources().contains("justjoin")) {
            var s = settings.justjoin();
            update(profile.resolve("justjoin.properties"), Map.of(
                    "mainTech", s.mainTech(), "location", s.location(), "publishedDays", s.publishedDays(),
                    "languages", String.join(",", s.languages()),
                    "experienceLevels", String.join(",", s.experienceLevels()),
                    "orderBy", s.orderBy(), "sortBy", s.sortBy(), "limit", Integer.toString(s.limit())));
        }
        update(root.resolve("Driver/collector/config/collector.properties"),
                Map.of("sources", String.join(",", settings.sources())));
    }

    private static void update(Path file, Map<String, String> entries) throws IOException {
        var properties = PropertySettings.load(file);
        entries.forEach(properties::setProperty);
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
    }
    static CollectionSettings load(ProjectPaths paths, String selectedSource) throws IOException {
        List<String> names = selectedSource == null ? loadSources(paths.collectorRoot()) : sources(selectedSource);
        return new CollectionSettings(names,
                names.contains("linkedin") ? CollectorConfig.loadSearch(paths.configPath()) : null,
                names.contains("justjoin") ? JustJoinConfig.loadSearch(paths.driverRoot().getParent()) : null);
    }
    static List<String> loadSources(Path collectorRoot) throws IOException {
        var properties = PropertySettings.load(collectorRoot.resolve("config/collector.properties"));
        return sources(properties.getProperty("sources", ""));
    }

    static List<String> sources(String value) {
        var sources = PropertySettings.csv(value);
        CollectionSettings.validateSources(sources);
        return sources;
    }
}
