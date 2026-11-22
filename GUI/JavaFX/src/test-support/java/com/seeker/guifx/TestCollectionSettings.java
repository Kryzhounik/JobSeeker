package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.core.CollectionSettingsLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.List;

final class TestCollectionSettings {
    static CollectionSettings create(Path root) throws IOException {
        Path config = root.resolve("Driver/collector/config");
        Path profile = root.resolve("Data/Profile/Driver/collector");
        for (Path file : List.of(config.resolve("collector.properties"),
                config.resolve("linkedin.properties"), config.resolve("justjoin.properties"),
                profile.resolve("linkedin.properties"), profile.resolve("justjoin.properties"))) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, "");
        }
        var settings = new CollectionSettings(List.of("linkedin", "justjoin"),
                new CollectionSettings.LinkedIn(List.of("Fixture Java"), List.of("Moldova:106178099"),
                        List.of("senior"), List.of("remote"), List.of("remote"),
                        List.of("full_time"), "week", "", 7),
                new CollectionSettings.JustJoin("java", "all-locations", "14", List.of("en"),
                        List.of("mid"), "DESC", "published", 3));
        CollectionSettingsLoader.save(root, settings);
        return settings;
    }
}
