package com.seeker.easyreasoning;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class DescriptionComparisonApplicationTest {
    @TempDir Path root;
    private String originalUserHome;

    @BeforeEach
    void useTemporaryUserHome() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", root.toString());
    }

    @AfterEach
    void restoreUserHome() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void applicationExposesComparisonUsingSharedBackendAndUnloadsOnClose() throws Exception {
        var json = JsonMapper.builder().build();
        List<JsonNode> requests = new ArrayList<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            var request = json.readTree(exchange.getRequestBody().readAllBytes());
            requests.add(request);
            boolean unload = "0".equals(request.path("keep_alive").asString());
            String answer = requests.size() == 1 ? "NO SUBSTANTIVE DIFFERENCES" : "SUBSTANTIVE DIFFERENCES";
            byte[] bytes = json.writeValueAsString(unload
                    ? Map.of("model", "qwen2.5:7b", "done", true)
                    : Map.of("model", "qwen2.5:7b", "done", true,
                    "message", Map.of("role", "assistant", "content", answer))).getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        Files.createDirectories(root.resolve("EasyReasoning/prompts"));
        Path prompt = Path.of("prompts/compare-vacancy-descriptions.md");
        String instruction = Files.readString(prompt);
        Files.copy(prompt, root.resolve("EasyReasoning/prompts/compare-vacancy-descriptions.md"));
        TestTokenizers.write(root.resolve(".ollama/tokenizers/qwen2.5-7b"));
        Files.writeString(root.resolve("EasyReasoning/easy-reasoning.properties"),
                "spring.ai.ollama.base-url=http://127.0.0.1:" + server.getAddress().getPort()
                        + "\nspring.ai.ollama.chat.model=qwen2.5:7b\n");
        server.createContext("/api/version", exchange -> {
            byte[] version = "{\"version\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, version.length);
            try (var output = exchange.getResponseBody()) { output.write(version); }
        });
        server.start();
        try {
            try (var app = new EasyReasoningApi(root)) {
                assertTrue(requests.isEmpty(), "Creating the gateway must not invoke the model");
                assertTrue(app.areDuplicateDescriptions("First\r\ntext", "Second text"));
                assertFalse(app.areDuplicateDescriptions("Other first", "Other second"));
            }
            assertEquals(3, requests.size());
            assertEquals(instruction + "\n\nAD 1:\nFirst\r\ntext\n\nAD 2:\nSecond text",
                    requests.getFirst().path("messages").get(1).path("content").asString());
            for (int n = 0; n < 2; n++) {
                assertEquals(2, requests.get(n).path("messages").size());
                assertEquals("qwen2.5:7b", requests.get(n).path("model").asString());
            }
            assertFalse(requests.get(1).path("messages").get(1).path("content").asString().contains("First\r\ntext"));
            assertEquals("0", requests.getLast().path("keep_alive").asString());
            var connection = (java.net.HttpURLConnection) java.net.URI.create(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/api/version").toURL().openConnection();
            try {
                assertEquals(200, connection.getResponseCode(), "Closing the API must preserve an external server");
            } finally {
                connection.disconnect();
            }
        } finally { server.stop(0); }
    }

}
