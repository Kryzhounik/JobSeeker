package com.seeker.collector.common;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record ProjectPaths(
        Path driverRoot,
        Path collectorRoot,
        Path configPath,
        Path databasePath,
        Path rawDirectory,
        Path profileDirectory
) {
    public static ProjectPaths fromProjectRoot(Path root) throws IOException {
        Path projectRoot = root.toAbsolutePath().normalize();
        Path driverRoot = projectRoot.resolve("Driver");
        Path collectorRoot = driverRoot.resolve("collector");
        Path configPath = collectorRoot.resolve("config/linkedin.properties");
        if (!Files.isRegularFile(configPath)) {
            throw new IllegalArgumentException(
                    "Project root does not contain Driver/collector/config/linkedin.properties: "
                            + projectRoot
            );
        }
        Properties sharedRuntime = new Properties();
        try (Reader reader = Files.newBufferedReader(projectRoot.resolve("Tools/runtime.properties"), StandardCharsets.UTF_8)) {
            sharedRuntime.load(reader);
        }
        Properties collectorSettings = new Properties();
        try (Reader reader = Files.newBufferedReader(collectorRoot.resolve("config/collector.properties"), StandardCharsets.UTF_8)) {
            collectorSettings.load(reader);
        }
        return new ProjectPaths(
                driverRoot,
                collectorRoot,
                configPath,
                path(projectRoot, sharedRuntime, "database.path"),
                path(projectRoot, collectorSettings, "raw.directory"),
                path(projectRoot, sharedRuntime, "profile.directory")
        );
    }

    private static Path path(Path projectRoot, Properties properties, String key) {
        Path path = Path.of(properties.getProperty(key));
        return path.isAbsolute()
                ? path.normalize()
                : projectRoot.resolve(path).normalize();
    }
}
