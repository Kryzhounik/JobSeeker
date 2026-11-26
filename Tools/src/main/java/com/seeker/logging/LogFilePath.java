package com.seeker.logging;

import ch.qos.logback.core.PropertyDefinerBase;
import java.nio.file.Path;
import java.time.Instant;

/** Resolves the process-owned log path before Logback creates its appenders. */
public final class LogFilePath extends PropertyDefinerBase {
    private static final String PROCESS = "java-" + ProcessHandle.current().pid() + "-"
            + Instant.now().toEpochMilli();

    @Override
    public String getPropertyValue() {
        return Path.of(getContext().getProperty("seeker.logging.root"))
                .resolve("Data/logs").resolve(PROCESS).toString();
    }

}
