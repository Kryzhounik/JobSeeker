package com.seeker.collector.linkedin.config;

import java.nio.file.Files;
import java.nio.file.Path;

public record ProjectPaths(
        Path projectRoot,
        Path driverRoot,
        Path collectorRoot,
        Path dataRoot,
        Path configPath,
        Path databasePath,
        Path rawDirectory,
        Path profileDirectory
) {
    public static ProjectPaths fromProjectRoot(Path root) {
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
        Path dataRoot = projectRoot.resolve("Data");
        return new ProjectPaths(
                projectRoot,
                driverRoot,
                collectorRoot,
                dataRoot,
                configPath,
                dataRoot.resolve("jobs.sqlite"),
                dataRoot.resolve("raw/linkedin"),
                dataRoot.resolve("browser_profiles/linkedin")
        );
    }

}
