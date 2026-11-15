package com.seeker.collector.common;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

/** Existing UTF-8 properties and comma-separated list conventions. */
public final class PropertySettings {
    private PropertySettings() { }

    public static Properties load(Path... files) throws IOException {
        var properties = new Properties();
        for (Path file : files) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { properties.load(reader); }
        }
        return properties;
    }

    public static List<String> csv(String value) {
        return Arrays.stream(value.split(",")).map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
