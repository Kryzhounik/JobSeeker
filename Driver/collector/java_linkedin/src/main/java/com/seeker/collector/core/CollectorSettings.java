package com.seeker.collector.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

/** Source selection only. Site settings and limits stay in their own configs. */
final class CollectorSettings {
    static List<String> loadSources(Path collectorRoot) throws IOException {
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(collectorRoot.resolve("config/collector.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return sources(properties.getProperty("sources", ""));
    }

    static List<String> sources(String value) {
        var sources = Arrays.stream(value.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
        if (sources.isEmpty() || new HashSet<>(sources).size() != sources.size()
                || sources.stream().anyMatch(s -> !List.of("linkedin", "justjoin").contains(s)))
            throw new IllegalArgumentException("Expected distinct sources: linkedin, justjoin");
        return sources;
    }
}
