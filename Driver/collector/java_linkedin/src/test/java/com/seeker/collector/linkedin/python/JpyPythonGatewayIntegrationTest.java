package com.seeker.collector.linkedin.python;

import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.config.ProjectPaths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class JpyPythonGatewayIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void callsExistingPythonPipelineInsideJvm() throws Exception {
        Path projectRoot = Path.of(required("seeker.project.root"));
        Path database = temporaryDirectory.resolve("jobs.sqlite");
        Path rawDirectory = temporaryDirectory.resolve("raw/linkedin");
        ProjectPaths paths = new ProjectPaths(
                projectRoot,
                projectRoot.resolve("Driver"),
                projectRoot.resolve("Driver/collector"),
                temporaryDirectory,
                projectRoot.resolve("Driver/collector/config/linkedin.properties"),
                database,
                rawDirectory,
                temporaryDirectory.resolve("browser-profile")
        );
        PythonRuntime runtime = new PythonRuntime(
                Path.of(required("seeker.python.library")),
                Path.of(required("seeker.jpy.library")),
                Path.of(required("seeker.jdl.library")),
                Path.of(required("seeker.python.home")),
                Path.of(required("seeker.python.executable"))
        );
        Preview preview = new Preview(
                "4444444444",
                "https://www.linkedin.com/jobs/view/4444444444/",
                "Java Developer",
                "Example Company",
                "Moldova",
                "remote",
                "",
                "integration",
                0,
                1
        );
        String html = """
                <!doctype html><html><head>
                <link rel="canonical" href="https://www.linkedin.com/jobs/view/4444444444/">
                </head><body><main><h1>Java Developer</h1><h2>About the job</h2>
                <p>Build backend services in Java.</p></main></body></html>
                """;

        try (PythonGateway python = new JpyPythonGateway(paths, runtime)) {
            assertTrue(python.priorityCompanyIds().isEmpty());
            assertTrue(python.decidePreview(preview).shouldOpen());
            assertEquals("raw_saved", python.processHtml(preview, html).status());
        }

        assertTrue(Files.isRegularFile(
                rawDirectory.resolve("pages/4444444444.html")
        ));
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}
