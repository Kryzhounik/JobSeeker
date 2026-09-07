package com.seeker.collector.linkedin.config;

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
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(
                collectorRoot.resolve("java_linkedin/runtime.properties"),
                StandardCharsets.UTF_8
        )) {
            properties.load(reader);
        }
        return new ProjectPaths(
                driverRoot,
                collectorRoot,
                configPath,
                path(projectRoot, properties, "database.path"),
                path(projectRoot, properties, "raw.directory"),
                path(projectRoot, properties, "profile.directory")
        );
    }

    private static Path path(Path projectRoot, Properties properties, String key) {
        Path path = Path.of(properties.getProperty(key));
        return path.isAbsolute()
                ? path.normalize()
                : projectRoot.resolve(path).normalize();
    }
}
