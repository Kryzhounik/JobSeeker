package com.seeker.collector.linkedin.application;

import com.seeker.tools.api.ProgressSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkedInApplicationTest {
    @Test
    void reusedApplicationReadsCurrentConfigForBatchAndUrlCollection(@TempDir Path root) throws Exception {
        Path collector = root.resolve("Driver/collector");
        Files.createDirectories(collector.resolve("config"));
        Files.createDirectories(collector.resolve("java_linkedin"));
        Files.writeString(collector.resolve("java_linkedin/runtime.properties"), """
                database.path=Data/jobs.sqlite
                raw.directory=Data/raw
                profile.directory=Data/profile
                python.library=unused-python.dll
                jpy.library=unused-jpy.dll
                jdl.library=unused-jdl.dll
                python.home=unused-python
                python.executable=unused-python.exe
                """);
        Path config = collector.resolve("config/linkedin.properties");
        Files.writeString(config, "limit=10\n");
        LinkedInApplication application = new LinkedInApplication(root);
        List<String> limits = new ArrayList<>();
        RuntimeException stop = new RuntimeException("Stop before Python and browser startup");
        Consumer<ProgressSnapshot> progress = update -> {
            limits.add(update.fields().get("Accepted / limit"));
            throw stop;
        };

        assertSame(stop, assertThrows(RuntimeException.class, () -> application.collectBatch(progress)));
        Files.writeString(config, "limit=20\n");
        assertSame(stop, assertThrows(RuntimeException.class, () -> application.collectBatch(progress)));
        Files.writeString(config, "limit=30\n");
        assertSame(stop, assertThrows(RuntimeException.class, () -> application.collectUrl(
                "url-run", "https://www.linkedin.com/jobs/view/1/", progress)));
        Files.writeString(config, "limit=40\n");
        assertSame(stop, assertThrows(RuntimeException.class, () -> application.collectUrls(
                List.of("https://www.linkedin.com/jobs/view/2/"), progress)));

        assertEquals(List.of("0 / 10", "0 / 20", "0 / 30", "0 / 40"), limits);
    }
}
