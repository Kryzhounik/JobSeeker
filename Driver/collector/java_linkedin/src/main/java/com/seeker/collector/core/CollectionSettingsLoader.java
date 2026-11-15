package com.seeker.collector.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import com.seeker.collector.common.ProjectPaths;
import com.seeker.collector.linkedin.config.CollectorConfig;
import com.seeker.collector.justjoin.JustJoinConfig;

/** Fresh default user preferences, loaded only for selected sources. */
final class CollectionSettingsLoader {
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
