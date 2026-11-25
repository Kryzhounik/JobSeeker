package com.seeker.easyreasoning.llm;

import com.seeker.easyreasoning.EasyReasoningConfiguration;
import com.seeker.easyreasoning.EasyReasoningService;
import com.seeker.easyreasoning.TestTokenizers;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.SYSTEM_PROPERTIES)
class OllamaConfigurationTest {
    @TempDir
    Path projectRoot;
    private String originalUserHome;

    @BeforeEach
    void useTemporaryUserHome() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", projectRoot.toString());
    }

    @AfterEach
    void restoreUserHome() {
        System.setProperty("user.home", originalUserHome);
    }

    @Test
    void wiresDefaultsWithoutOllama() {
        try (var context = context(Map.of())) {
            context.refresh();
            ChatModel model = context.getBean(ChatModel.class);
            assertInstanceOf(OllamaChatModel.class, model);
            assertEquals("qwen2.5:7b", model.getOptions().getModel());
            assertNotNull(context.getBean(EasyReasoningService.class));
        }
    }

    @Test
    void usesConfigFileEndpointAndModelOnlyWhenCalled() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestTarget = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            requestTarget.set(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            requestBody.set(exchange.getRequestBody().readAllBytes());
            byte[] response = """
                    {"model":"test-model","message":{"role":"assistant","content":"  Response\\n"},"done":true}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.createContext("/api/version", exchange -> {
            byte[] version = "{\"version\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, version.length);
            try (var output = exchange.getResponseBody()) { output.write(version); }
        });
        server.start();
        try (var context = context(Map.of())) {
            Files.createDirectories(projectRoot.resolve("EasyReasoning"));
            Files.writeString(projectRoot.resolve("EasyReasoning/easy-reasoning.properties"), """
                    spring.ai.ollama.base-url=http://127.0.0.1:%d
                    spring.ai.ollama.chat.model=test-model
                    """.formatted(server.getAddress().getPort()));
            context.refresh();
            assertEquals(0, calls.get(), "Context initialization must not contact Ollama");
            assertEquals("  Response\n", context.getBean(EasyReasoningService.class).generate("Check"));
            assertEquals(1, calls.get());
            assertEquals("POST /api/chat", requestTarget.get());
            var json = JsonMapper.builder().build().readTree(requestBody.get());
            assertEquals("test-model", json.path("model").asString());
            assertEquals("Check", json.path("messages").get(0).path("content").asString());
            assertFalse(json.path("stream").asBoolean());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void supportsEnvironmentVariableNamesAndPropagatesProviderFailure() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":\"model not found\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(404, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.createContext("/api/version", exchange -> {
            byte[] version = "{\"version\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, version.length);
            try (var output = exchange.getResponseBody()) { output.write(version); }
        });
        server.start();
        try (var context = context(Map.of())) {
            Files.createDirectories(projectRoot.resolve("EasyReasoning"));
            Files.writeString(projectRoot.resolve("EasyReasoning/easy-reasoning.properties"), """
                    spring.ai.ollama.base-url=http://127.0.0.1:1
                    spring.ai.ollama.chat.model=file-model
                    """);
            context.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource(
                    "testEnvironment", Map.of(
                            "SPRING_AI_OLLAMA_BASE_URL", "http://127.0.0.1:" + server.getAddress().getPort(),
                            "SPRING_AI_OLLAMA_CHAT_MODEL", "missing-model")));
            context.refresh();
            assertEquals("missing-model", context.getBean(ChatModel.class).getOptions().getModel());
            assertThrows(NonTransientAiException.class,
                    () -> context.getBean(EasyReasoningService.class).generate("Hello"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void comparisonSendsExplicitMessagesAndConfiguredContextToFakeOllama() throws Exception {
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/version", exchange -> {
            calls.incrementAndGet();
            byte[] version = "{\"version\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, version.length);
            try (var output = exchange.getResponseBody()) { output.write(version); }
        });
        server.createContext("/api/chat", exchange -> {
            calls.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            if (!new String(body, StandardCharsets.UTF_8).contains("\"keep_alive\":\"0\"")) requestBody.set(body);
            byte[] response = "{\"model\":\"qwen2.5:7b\",\"message\":{\"role\":\"assistant\",\"content\":\"NO SUBSTANTIVE DIFFERENCES\"},\"done\":true}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            installQwenTokenizer();
            Files.createDirectories(projectRoot.resolve("EasyReasoning"));
            Files.writeString(projectRoot.resolve("EasyReasoning/easy-reasoning.properties"), """
                    spring.ai.ollama.base-url=http://127.0.0.1:%d
                    spring.ai.ollama.chat.model=qwen2.5:7b
                    spring.ai.ollama.chat.options.num-ctx=4096
                    """.formatted(server.getAddress().getPort()));
            try (var context = context(Map.of())) {
                context.refresh();
                assertEquals("NO SUBSTANTIVE DIFFERENCES",
                        context.getBean(EasyReasoningService.class).generateComparison("AD 1: Java\nAD 2: Python"));
            }
            var request = JsonMapper.builder().build().readTree(requestBody.get());
            assertEquals(2, request.path("messages").size());
            assertEquals(QwenRequestBudget.SYSTEM, request.path("messages").get(0).path("content").asString());
            assertEquals("AD 1: Java\nAD 2: Python", request.path("messages").get(1).path("content").asString());
            assertEquals(4096, request.path("options").path("num_ctx").asInt());
            assertTrue(request.path("options").path("num_predict").asInt() > 1);
            assertEquals(3, calls.get());
        } finally { server.stop(0); }
    }

    @Test
    void oversizedComparisonIsRejectedBeforeOllamaReadinessOrChat() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) { output.write(response); }
        });
        server.start();
        try {
            installQwenTokenizer();
            Files.createDirectories(projectRoot.resolve("EasyReasoning"));
            Files.writeString(projectRoot.resolve("EasyReasoning/easy-reasoning.properties"), """
                    spring.ai.ollama.base-url=http://127.0.0.1:%d
                    spring.ai.ollama.chat.model=qwen2.5:7b
                    spring.ai.ollama.chat.options.num-ctx=32
                    """.formatted(server.getAddress().getPort()));
            try (var context = context(Map.of())) {
                context.refresh();
                var service = context.getBean(EasyReasoningService.class);
                assertThrows(com.seeker.easyreasoning.RequestTooLargeException.class,
                        () -> service.generateComparison("漢".repeat(200)));
            }
            assertEquals(0, calls.get(), "Budget rejection must happen before version or chat requests");
        } finally { server.stop(0); }
    }

    @Test
    void configuredModelDoesNotFallBackToAnotherModelsTokenizer() throws Exception {
        TestTokenizers.write(projectRoot.resolve(".ollama/tokenizers/qwen2.5-7b"));
        Files.createDirectories(projectRoot.resolve("EasyReasoning"));
        Files.writeString(projectRoot.resolve("EasyReasoning/easy-reasoning.properties"),
                "spring.ai.ollama.chat.model=qwen2.5:3b\n");
        Path expected = projectRoot.resolve(".ollama/tokenizers/qwen2.5-3b/tokenizer.json").toAbsolutePath().normalize();
        try (var context = context(Map.of())) {
            context.refresh();
            var service = context.getBean(EasyReasoningService.class);
            var error = assertThrows(java.io.UncheckedIOException.class,
                    () -> service.generateComparison("prompt"));
            assertTrue(error.getMessage().contains(expected.toString()));
            service.close();
        }
    }

    @Test
    void reportsUnavailableLocalServerWithoutExecutable() throws Exception {
        int port;
        try (var socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            port = socket.getLocalPort();
        }
        try (var context = context(Map.of(
                "spring.ai.ollama.base-url", "http://127.0.0.1:" + port))) {
            context.refresh();
            EasyReasoningService service = context.getBean(EasyReasoningService.class);
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> {
                        var error = assertThrows(IllegalStateException.class, () -> service.generate("Hello"));
                        assertTrue(error.getMessage().contains("local executable not found"));
                        service.close();
                    });
        }
    }

    @Test
    void makesOnlyOneAttemptOnTransientServerFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> {
            calls.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            byte[] response = "{\"error\":\"unavailable\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        server.createContext("/api/version", exchange -> {
            byte[] version = "{\"version\":\"test\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, version.length);
            try (var output = exchange.getResponseBody()) { output.write(version); }
        });
        server.start();
        try (var context = context(Map.of(
                "spring.ai.ollama.base-url", "http://127.0.0.1:" + server.getAddress().getPort()))) {
            context.refresh();
            EasyReasoningService service = context.getBean(EasyReasoningService.class);
            assertTimeoutPreemptively(Duration.ofSeconds(5),
                    () -> assertThrows(TransientAiException.class, () -> service.generate("Hello")));
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    private AnnotationConfigApplicationContext context(Map<String, Object> properties) {
        var context = new AnnotationConfigApplicationContext();
        var sources = context.getEnvironment().getPropertySources();
        sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        sources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        sources.addFirst(new MapPropertySource("testSettings", properties));
        context.registerBean(Path.class, () -> projectRoot);
        context.register(EasyReasoningConfiguration.class);
        return context;
    }

    private void installQwenTokenizer() throws Exception {
        TestTokenizers.write(projectRoot.resolve(".ollama/tokenizers/qwen2.5-7b"));
    }
}
