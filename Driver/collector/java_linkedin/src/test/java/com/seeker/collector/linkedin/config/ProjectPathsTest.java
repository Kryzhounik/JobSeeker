package com.seeker.collector.linkedin.config;
import com.seeker.collector.common.ProjectPaths;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ProjectPathsTest {
    @Test void separatesSharedDatabaseFromCollectorPaths(@TempDir Path root) throws Exception {
        Path collector = Files.createDirectories(root.resolve("Driver/collector/java_linkedin"));
        Files.createDirectories(root.resolve("Driver/collector/config"));
        Files.writeString(root.resolve("Driver/collector/config/linkedin.properties"), "");
        Files.writeString(collector.resolve("runtime.properties"),
                "raw.directory=output/html\nprofile.directory=browser/profile\n");
        Files.createDirectories(root.resolve("Tools"));
        Files.writeString(root.resolve("Tools/runtime.properties"), "database.path=storage/custom jobs.sqlite\n");
        ProjectPaths paths = ProjectPaths.fromProjectRoot(root);
        assertEquals(root.resolve("storage/custom jobs.sqlite"), paths.databasePath());
        assertEquals(root.resolve("output/html"), paths.rawDirectory());
        assertEquals(root.resolve("browser/profile"), paths.profileDirectory());
    }
}
