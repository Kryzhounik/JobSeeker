package com.seeker.orchestrator;

import com.seeker.collector.linkedin.python.PythonRuntime;
import com.seeker.codex.CodexClient;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.python.JpyRuntime;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class JpyOrchestratorBridgeIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void importsOrchestratorBridgeThroughSharedRuntime() {
        startRuntime();
        Path database = temporaryDirectory.resolve("jobs.sqlite");

        try (PyModule bridge = PyModule.importModule("orchestrator.python_bridge");
             PyObject ignored = bridge.callMethod(
                     "load_statuses",
                     "linkedin",
                     "[]",
                     database.toString()
             )) {
            // Import and call through jpy are the integration boundary under test.
        }

        assertTrue(Files.isRegularFile(database));
    }

    @Test
    void sharedCodexClientTranslatesThePythonLimitThroughJpy() throws Exception {
        Files.writeString(temporaryDirectory.resolve("codex_client_fixture.py"), """
                from datetime import datetime, timezone
                from codex_proxy.errors import UsageLimitExceededError

                def echo(value):
                    return value

                def limited():
                    raise UsageLimitExceededError("Quota exhausted", datetime(2026, 10, 22, tzinfo=timezone.utc))

                def broken():
                    raise ValueError("Unrelated failure")
                """);
        startRuntime();
        try (PyModule module = PyModule.importModule("codex_client_fixture")) {
            assertEquals("thread-1", CodexClient.call(module, "echo", "thread-1"));
            UsageLimitExceededException error = assertThrows(UsageLimitExceededException.class,
                    () -> CodexClient.call(module, "limited"));
            assertEquals(Instant.parse("2026-10-22T00:00:00Z"), error.getRetryAt());
            assertThrows(RuntimeException.class, () -> CodexClient.call(module, "broken"));
            assertEquals("thread-2", CodexClient.call(module, "echo", "thread-2"));
        }
    }

    private void startRuntime() {
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
                projectRoot.resolve("Driver"), temporaryDirectory
        );
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}
