package com.seeker.easyreasoning.llm;

import com.seeker.easyreasoning.EasyReasoningConfiguration;
import com.seeker.easyreasoning.EasyReasoningService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.web.client.ResourceAccessException;
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

class OllamaConfigurationTest {
    @TempDir
    Path projectRoot;

    @Test
    void wiresDefaultsWithoutOllama() {
        try (var context = context(Map.of())) {
            context.refresh();
            ChatModel model = context.getBean(ChatModel.class);
            assertInstanceOf(OllamaChatModel.class, model);
            assertEquals("qwen2.5:0.5b", model.getOptions().getModel());
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
                    {"model":"test-model","message":{"role":"assistant","content":"  Ответ\\n"},"done":true}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
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
            assertEquals("  Ответ\n", context.getBean(EasyReasoningService.class).generate("Проверка"));
            assertEquals(1, calls.get());
            assertEquals("POST /api/chat", requestTarget.get());
            var json = JsonMapper.builder().build().readTree(requestBody.get());
            assertEquals("test-model", json.path("model").asString());
            assertEquals("Проверка", json.path("messages").get(0).path("content").asString());
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
    void doesNotRetryConnectionFailure() throws Exception {
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
                    () -> assertThrows(ResourceAccessException.class, () -> service.generate("Hello")));
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
}
