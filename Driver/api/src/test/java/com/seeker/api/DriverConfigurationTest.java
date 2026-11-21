package com.seeker.api;

import com.seeker.easyreasoning.EasyReasoningConfiguration;
import com.seeker.easyreasoning.EasyReasoningService;
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
    void wiresFacadeAndEasyReasoningConfigurationWithoutStartingExternalRuntimes() throws Exception {
        Path collector = Files.createDirectories(projectRoot.resolve("Driver/collector"));
        Files.createDirectories(collector.resolve("config"));
        Files.writeString(collector.resolve("config/linkedin.properties"), "");
        Files.writeString(collector.resolve("config/collector.properties"), "sources=linkedin\nraw.directory=Data/raw\n");
        Files.createDirectories(projectRoot.resolve("Tools"));
        Files.writeString(projectRoot.resolve("Tools/runtime.properties"), """
                database.path=Data/jobs.sqlite
                profile.directory=Data/profile
                python.library=unused/python.dll
                jpy.library=unused/jpy.dll
                jdl.library=unused/jdl.dll
                python.home=unused
                python.executable=unused/python.exe
                """);
        Path analyzer = Files.createDirectories(projectRoot.resolve("Driver/analyzer/config"));
        Files.writeString(analyzer.resolve("execution.ini"), "parallel_agents=1\nvacancies_per_agent=1\n");

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
            assertNotNull(context.getBean(EasyReasoningConfiguration.class));
            assertNotNull(context.getBean(EasyReasoningService.class));
            assertEquals(1, context.getBeansOfType(DriverApi.class).size());
            var explicit = new com.seeker.collector.common.CollectionSettings(java.util.List.of("linkedin"),
                    new com.seeker.collector.common.CollectionSettings.LinkedIn(java.util.List.of("Java"), java.util.List.of("Poland"),
                            java.util.List.of(), java.util.List.of("remote"), java.util.List.of("remote"),
                            java.util.List.of(), "week", "", 23), null);
            var stop = new RuntimeException("Stop before Python or browser startup");
            assertSame(stop, assertThrows(RuntimeException.class, () -> api.collectBatch(explicit, update -> {
                assertEquals("0 / 23", update.fields().get("Accepted / limit"));
                throw stop;
            })));
            // Construction works with nonexistent Python/native paths: no runtime was started.
            assertNotNull(new DriverApi(projectRoot));
        }
        assertFalse(context.isActive());
        assertEquals(1, closed.get());
        assertThrows(IllegalStateException.class, () -> context.getBean(DriverApi.class));
    }
}
