package com.seeker.guifx;

import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

/** Collect operation independent of its transport and of JavaFX. */
interface CollectorClient {
    CollectionReport collect(Consumer<ProgressSnapshot> progress) throws Exception;

    static CollectorClient configured(Path projectRoot, CollectorClient direct) throws IOException {
        Path config = projectRoot.resolve("GUI/JavaFX/backend.properties");
        Properties values = new Properties();
        if (Files.exists(config)) {
            try (Reader reader = Files.newBufferedReader(config, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
        }
        return switch (values.getProperty("collector.mode", "direct").strip()) {
            case "direct" -> java.util.Objects.requireNonNull(direct);
            case "process" -> new ProcessCollectorClient(projectRoot);
            default -> throw new IOException("Unknown collector.mode in " + config);
        };
    }
}
