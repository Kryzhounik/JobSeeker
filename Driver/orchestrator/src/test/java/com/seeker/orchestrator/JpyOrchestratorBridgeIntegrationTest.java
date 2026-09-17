package com.seeker.orchestrator;

import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.python.JpyRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class JpyOrchestratorBridgeIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsOrchestratorBridgeThroughSharedRuntime() {
        Path projectRoot = Path.of(required("seeker.project.root"));
        PythonRuntime runtime = new PythonRuntime(
                Path.of(required("seeker.python.library")),
                Path.of(required("seeker.jpy.library")),
                Path.of(required("seeker.jdl.library")),
                Path.of(required("seeker.python.home")),
                Path.of(required("seeker.python.executable"))
        );
        JpyRuntime.ensureStarted(
                runtime.pythonLibrary(),
                runtime.jpyLibrary(),
                runtime.jdlLibrary(),
                runtime.pythonHome(),
                runtime.pythonExecutable(),
                projectRoot.resolve("Driver")
        );
        Path database = temporaryDirectory.resolve("jobs.sqlite");

        try (PyModule bridge = PyModule.importModule("orchestrator.python_bridge");
             PyObject ignored = bridge.callMethod(
                     "mark_nonrelevant",
                     database.toString(),
                     "linkedin",
                     "[\"integration-1\"]"
             )) {
            // Import and call through jpy are the integration boundary under test.
        }

        assertTrue(Files.isRegularFile(database));
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}
