package com.seeker.easyreasoning;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EasyReasoningCliTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path root;

    @Test
    void reusesOneContextForTwoRequestsAndUnloadsOnStop() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, false, false);
        try {
            var output = run("""
                    generate "Первая строка
                    Вторая строка"
                    generate Another request
                    stop
                    generate Must not run
                    """);
            assertEquals("  Ответ\n  Ответ\n", output.text());
            assertEquals(0, output.exitCode());
            assertTrue(output.errors().startsWith("Ready."));
            assertTrue(output.errors().contains("Stopped."));
            assertFalse(output.errors().contains("Error:"));
            assertEquals(3, requests.size());
            for (int index = 0; index < 2; index++) {
                assertEquals("test-model", requests.get(index).path("model").asString());
                assertEquals("-1m", requests.get(index).path("keep_alive").asString());
                assertEquals(1, requests.get(index).path("messages").size());
            }
            assertEquals("Первая строка\nВторая строка",
                    requests.get(0).path("messages").get(0).path("content").asString());
            assertEquals("Another request",
                    requests.get(1).path("messages").get(0).path("content").asString());
            assertEquals("0", requests.get(2).path("keep_alive").asString());
            assertEquals(0, requests.get(2).path("messages").size());
            assertEquals("test-model", requests.get(2).path("model").asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void reportsCommandAndRequestErrorsThenContinuesAndUnloadsOnEof() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, true, false);
        try {
            var output = run("""
                    unknown
                    generate "Fails"
                    generate "Works"
                    """);
            assertEquals("  Ответ\n", output.text());
            assertEquals(1, output.exitCode(), "Later success must not erase an earlier error");
            assertTrue(output.errors().contains("Usage: generate"));
            assertEquals(2, output.errors().lines().filter(line -> line.startsWith("Error:")).count());
            assertTrue(output.errors().contains("Stopped."));
            assertEquals(3, requests.size(), "Failed generation must not be retried");
            assertEquals("0", requests.get(2).path("keep_alive").asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unloadsAfterFailedGenerationAttempt() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, true, false);
        try {
            var output = run("""
                    generate "Fails"
                    stop
                    """);
            assertEquals("", output.text());
            assertEquals(1, output.exitCode());
            assertTrue(output.errors().contains("Error:"));
            assertTrue(output.errors().contains("Stopped."));
            assertEquals(2, requests.size());
            assertEquals("0", requests.get(1).path("keep_alive").asString());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unloadFailureIsFatalAndDoesNotAcknowledgeSuccessfulStop() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, false, true);
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        try {
            assertThrows(RuntimeException.class, () -> EasyReasoningCli.run(root,
                    new BufferedReader(new StringReader("""
                            generate "Hello"
                            stop
                            """)), new PrintWriter(output), new PrintWriter(errors)));
            assertEquals("  Ответ\n", output.toString());
            assertFalse(errors.toString().contains("Stopped."));
            assertEquals(2, requests.size(), "Unload is attempted once");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(List<JsonNode> requests, boolean failFirst, boolean failUnload) throws Exception {
        return server(requests, failFirst, failUnload, "  Ответ\n");
    }

    private HttpServer server(List<JsonNode> requests, boolean failFirst, boolean failUnload, String answer) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            boolean unload = "0".equals(request.path("keep_alive").asString());
            boolean failure = (failFirst && requests.size() == 1) || (failUnload && unload);
            String response = failure ? "{\"error\":\"unavailable\"}"
                    : unload ? "{\"model\":\"test-model\",\"done\":true,\"done_reason\":\"unload\"}"
                    : JSON.writeValueAsString(java.util.Map.of("model", "test-model", "message",
                            java.util.Map.of("role", "assistant", "content", answer), "done", true));
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failure ? 503 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        Files.createDirectories(root.resolve("EasyReasoning"));
        Files.writeString(root.resolve("EasyReasoning/easy-reasoning.properties"), """
                spring.ai.ollama.base-url=http://127.0.0.1:%d
                spring.ai.ollama.chat.model=test-model
                """.formatted(server.getAddress().getPort()));
        server.start();
        return server;
    }

    @Test
    void acceptsQuotedTextAndReportsUnclosedQuoteWithoutCallingModel() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, false, false);
        try {
            var output = run("""
                    generate "Скажи \\"привет\\" по пути C:\\Users\\user."
                    generate "Unclosed
                    """);
            assertEquals("  Ответ\n", output.text());
            assertTrue(output.errors().contains("Unclosed quote"));
            assertEquals(1, output.exitCode());
            assertEquals(2, requests.size());
            assertEquals("Скажи \"привет\" по пути C:\\Users\\user.",
                    requests.get(0).path("messages").get(0).path("content").asString());
        } finally {
            server.stop(0);
        }
    }

    private CliOutput run(String commands) throws Exception {
        StringWriter output = new StringWriter();
        StringWriter errors = new StringWriter();
        int exitCode = EasyReasoningCli.run(root, new BufferedReader(new StringReader(commands)),
                new PrintWriter(output), new PrintWriter(errors));
        return new CliOutput(output.toString(), errors.toString(), exitCode);
    }

    private record CliOutput(String text, String errors, int exitCode) {}

    @Test
    void cleanVacancyCommandUsesConfiguredSwitchAndSameGenerationBackend() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, false, false, "{\"from_line\":2,\"to_line\":2}");
        try {
            var disabled = run("clean-vacancy \"before\nJava developer\nafter\"\nstop\n");
            assertEquals(0, disabled.exitCode());
            assertEquals("before\nJava developer\nafter" + System.lineSeparator(), disabled.text());
            assertEquals(0, requests.size());
            Files.createDirectories(root.resolve("EasyReasoning/prompts"));
            Files.writeString(root.resolve("EasyReasoning/prompts/clean-vacancy.md"),
                    "Select vacancy boundaries as from_line and to_line.\nNumbered source:\n");
            Files.writeString(root.resolve("EasyReasoning/easy-reasoning.properties"),
                    "vacancy.trimming.enabled=true\n", java.nio.file.StandardOpenOption.APPEND);
            var enabled = run("clean-vacancy \"before\nJava developer\nafter\"\nstop\n");
            assertEquals(0, enabled.exitCode());
            assertEquals("Java developer\n", enabled.text());
            assertEquals(2, requests.size());
            assertTrue(requests.getFirst().path("messages").get(0).path("content").asString().contains("2: Java developer"));
            assertEquals("0", requests.getLast().path("keep_alive").asString());
        } finally { server.stop(0); }
    }
}
