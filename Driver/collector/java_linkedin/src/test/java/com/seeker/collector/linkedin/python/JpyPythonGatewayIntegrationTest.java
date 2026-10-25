package com.seeker.collector.linkedin.python;

import com.seeker.collector.linkedin.collection.Preview;
import com.seeker.collector.linkedin.config.ProjectPaths;
import com.seeker.collector.linkedin.support.CliOutput;
import com.seeker.python.JpyRuntime;
import com.sun.net.httpserver.HttpServer;
import org.jpy.PyModule;
import org.jpy.PyObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "seeker.jpy.integration", matches = "true")
class JpyPythonGatewayIntegrationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void callsExistingPythonPipelineAndReusesItsRuntimeInsideJvm() throws Exception {
        Path projectRoot = Path.of(required("seeker.project.root"));
        Path database = temporaryDirectory.resolve("jobs.sqlite");
        Path rawDirectory = temporaryDirectory.resolve("raw/linkedin");
        ProjectPaths paths = new ProjectPaths(
                projectRoot.resolve("Driver"),
                projectRoot.resolve("Driver/collector"),
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
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot);
        String originalStdout = stdoutId();
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
            assertEquals(originalStdout, stdoutId());
            assertTrue(python.priorityCompanyIds().isEmpty());
            assertTrue(python.decidePreview(preview).shouldOpen());
            assertEquals("raw_saved", python.processHtml(preview, html, true).status());
            assertEquals(originalStdout, stdoutId());
        }
        try (PythonGateway python = new JpyPythonGateway(paths, runtime)) {
            assertTrue(python.priorityCompanyIds().isEmpty());
        }
        assertEquals(originalStdout, stdoutId());
        AtomicInteger modelCalls = new AtomicInteger();
        var model = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        model.createContext("/api/chat", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            modelCalls.incrementAndGet();
            String response = request.contains("\"keep_alive\":\"0\"")
                    ? "{\"model\":\"test-model\",\"done\":true}"
                    : "{\"model\":\"test-model\",\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"from_line\\\":1,\\\"to_line\\\":1}\"}}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        model.start();
        String oldEnabled = System.getProperty("vacancy.trimming.enabled");
        String oldUrl = System.getProperty("spring.ai.ollama.base-url");
        try {
            System.setProperty("vacancy.trimming.enabled", "true");
            System.setProperty("spring.ai.ollama.base-url", "http://127.0.0.1:" + model.getAddress().getPort());
            Preview trimmedPreview = new Preview("4444444445", "https://www.linkedin.com/jobs/view/4444444445/",
                    preview.title(), preview.company(), preview.location(), preview.workplace(),
                    preview.salary(), preview.label(), preview.start(), preview.index());
            try (PythonGateway python = new JpyPythonGateway(paths, runtime)) {
                python.processHtml(trimmedPreview, html.replace("4444444444", "4444444445"), true);
                assertEquals(1, modelCalls.get());
                try (PyModule sqlite = PyModule.importModule("sqlite3");
                     PyModule texts = PyModule.importModule("db.readable_text");
                     PyObject connection = sqlite.callMethod("connect", database.toString())) {
                    try (PyObject text = texts.callMethod("load_readable_text", connection, "linkedin", trimmedPreview.jobId())) {
                        assertEquals(1, text.getStringValue().lines().count());
                    } finally {
                        try (PyObject ignored = connection.callMethod("close")) { }
                    }
                }
            }
            assertEquals(2, modelCalls.get(), "The collector must unload the used model on close");
        } finally {
            if (oldEnabled == null) System.clearProperty("vacancy.trimming.enabled");
            else System.setProperty("vacancy.trimming.enabled", oldEnabled);
            if (oldUrl == null) System.clearProperty("spring.ai.ollama.base-url");
            else System.setProperty("spring.ai.ollama.base-url", oldUrl);
            model.stop(0);
        }
        try (CliOutput ignored = CliOutput.redirectPython(projectRoot);
             PyModule sys = PyModule.importModule("sys");
             PyObject stderr = sys.getAttribute("stderr")) {
            assertEquals(objectId(stderr), stdoutId());
        }
        assertEquals(originalStdout, stdoutId());

        assertTrue(Files.isRegularFile(
                rawDirectory.resolve("pages/4444444444.html")
        ));
    }

    private static String stdoutId() {
        try (PyModule sys = PyModule.importModule("sys");
             PyObject stdout = sys.getAttribute("stdout")) {
            return objectId(stdout);
        }
    }

    private static String objectId(PyObject value) {
        try (PyModule builtins = PyModule.importModule("builtins");
             PyObject id = builtins.callMethod("id", value)) {
            return id.repr();
        }
    }

    private String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}
