package com.seeker.api;

import com.seeker.llm.LlmConfiguration;
import com.seeker.llm.LlmService;
import org.springframework.ai.chat.model.ChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DriverConfigurationTest {
    @TempDir
    Path projectRoot;

    @Test
    void wiresFacadeAndLlmConfigurationWithoutStartingExternalRuntimes() throws Exception {
        Path collector = Files.createDirectories(projectRoot.resolve("Driver/collector"));
        Files.createDirectories(collector.resolve("config"));
        Files.writeString(collector.resolve("config/linkedin.properties"), "");
        Files.createDirectories(collector.resolve("java_linkedin"));
        Files.writeString(collector.resolve("java_linkedin/runtime.properties"), """
                database.path=Data/jobs.sqlite
                raw.directory=Data/raw
                profile.directory=Data/profile
                python.library=unused/python.dll
                jpy.library=unused/jpy.dll
                jdl.library=unused/jdl.dll
                python.home=unused
                python.executable=unused/python.exe
                """);
        Path analyzer = Files.createDirectories(projectRoot.resolve("Driver/analyzer/config"));
        Files.writeString(analyzer.resolve("execution.ini"), "vacancies_per_agent=1\n");

        AtomicInteger closed = new AtomicInteger();
        var context = new AnnotationConfigApplicationContext();
        try (context) {
            context.registerBean(Path.class, () -> projectRoot);
            context.register(DriverConfiguration.class);
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) closed.incrementAndGet();
            });
            context.refresh();

            assertTrue(context.isActive());
            DriverApi api = context.getBean(DriverApi.class);
            assertSame(api, context.getBean(DriverApi.class));
            assertNotNull(context.getBean(LlmConfiguration.class));
            assertNotNull(context.getBean(ChatModel.class));
            assertNotNull(context.getBean(LlmService.class));
            assertEquals(1, context.getBeansOfType(DriverApi.class).size());
            // Construction works with nonexistent Python/native paths: no runtime was started.
            assertNotNull(new DriverApi(projectRoot));
        }
        assertFalse(context.isActive());
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, () -> context.getBean(DriverApi.class));
    }
}
