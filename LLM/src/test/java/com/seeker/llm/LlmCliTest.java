package com.seeker.llm;

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

class LlmCliTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path root;

    @Test
    void reusesOneContextForTwoRequestsAndUnloadsOnStop() throws Exception {
        List<JsonNode> requests = new ArrayList<>();
        var server = server(requests, false, false);
        try {
            var output = run("""
                    {"command":"generate","prompt":"Первая строка\\nВторая строка"}
                    {"command":"generate","prompt":"Another request"}
                    {"command":"stop"}
                    {"command":"generate","prompt":"Must not run"}
                    """);
            assertEquals(List.of("ready", "ok", "ok", "stopped"), statuses(output));
            assertEquals("  Ответ\n", output.get(1).path("text").asString());
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
                    not json
                    {"command":"generate","prompt":"Fails"}
                    {"command":"generate","prompt":"Works"}
                    """);
            assertEquals(List.of("ready", "error", "error", "ok", "stopped"), statuses(output));
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
                    {"command":"generate","prompt":"Fails"}
                    {"command":"stop"}
                    """);
            assertEquals(List.of("ready", "error", "stopped"), statuses(output));
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
        try {
            assertThrows(RuntimeException.class, () -> LlmCli.run(root,
                    new BufferedReader(new StringReader("""
                            {"command":"generate","prompt":"Hello"}
                            {"command":"stop"}
                            """)), new PrintWriter(output)));
            assertEquals(List.of("ready", "ok"), statuses(parse(output)));
            assertEquals(2, requests.size(), "Unload is attempted once");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer server(List<JsonNode> requests, boolean failFirst, boolean failUnload) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            var request = JSON.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            boolean unload = "0".equals(request.path("keep_alive").asString());
            boolean failure = (failFirst && requests.size() == 1) || (failUnload && unload);
            String response = failure ? "{\"error\":\"unavailable\"}"
                    : unload ? "{\"model\":\"test-model\",\"done\":true,\"done_reason\":\"unload\"}"
                    : "{\"model\":\"test-model\",\"message\":{\"role\":\"assistant\",\"content\":\"  Ответ\\n\"},\"done\":true}";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(failure ? 503 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        Files.createDirectories(root.resolve("LLM"));
        Files.writeString(root.resolve("LLM/llm.properties"), """
                spring.ai.ollama.base-url=http://127.0.0.1:%d
                spring.ai.ollama.chat.model=test-model
                """.formatted(server.getAddress().getPort()));
        server.start();
        return server;
    }

    private List<JsonNode> run(String commands) throws Exception {
        StringWriter output = new StringWriter();
        LlmCli.run(root, new BufferedReader(new StringReader(commands)), new PrintWriter(output));
        return parse(output);
    }

    private List<JsonNode> parse(StringWriter output) {
        return output.toString().lines().map(JSON::readTree).toList();
    }

    private List<String> statuses(List<JsonNode> output) {
        return output.stream().map(line -> line.path("status").asString()).toList();
    }
}
