package com.seeker.collector.linkedin.python;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seeker.python.JpyRuntime;
import com.seeker.python.PythonRuntime;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jpy.PyModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PythonLoggingJpyIntegrationTest {
    private static final String LOGGER_NAME = "com.seeker.logging.jpy-bridge-test";

    @TempDir
    Path temporaryDirectory;

    @Test
    void repeatedRuntimeInitializationForwardsOnePythonTracebackWithoutChangingStreams() throws Exception {
        Path projectRoot = Path.of(required("seeker.project.root")).toAbsolutePath().normalize();
        Files.writeString(temporaryDirectory.resolve("python_logging_fixture.py"), """
                import logging

                def emit(logger_name):
                    try:
                        raise ValueError("jpy-bridge-traceback")
                    except ValueError:
                        logging.getLogger(logger_name).exception("jpy-bridge-marker")
                """);
        PythonRuntime runtime = PythonRuntime.load(projectRoot.resolve("Tools/runtime.properties"));
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot, projectRoot.resolve("Driver"),
                temporaryDirectory);
        String stdoutBefore = streamId("stdout");
        String stderrBefore = streamId("stderr");

        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot, projectRoot.resolve("Driver"),
                temporaryDirectory);
        JpyRuntime.ensureStarted(runtime.pythonLibrary(), runtime.jpyLibrary(), runtime.jdlLibrary(),
                runtime.pythonHome(), runtime.pythonExecutable(), projectRoot, projectRoot.resolve("Driver"),
                temporaryDirectory);

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(LOGGER_NAME);
        boolean originalAdditivity = logger.isAdditive();
        Level originalLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.start();
        logger.setAdditive(false);
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);

        try {
            try (PyModule fixture = PyModule.importModule("python_logging_fixture")) {
                try (var ignored = fixture.callMethod("emit", LOGGER_NAME)) {
                    // The Python exception is recorded through logging, not returned to Java.
                }
            }

            assertEquals(stdoutBefore, streamId("stdout"));
            assertEquals(stderrBefore, streamId("stderr"));
            assertEquals(1, appender.list.size());
            ILoggingEvent event = appender.list.getFirst();
            assertEquals(LOGGER_NAME, event.getLoggerName());
            assertEquals(Level.ERROR, event.getLevel());
            assertTrue(event.getFormattedMessage().contains("jpy-bridge-marker"));
            assertTrue(event.getFormattedMessage().contains("Traceback (most recent call last):"));
            assertTrue(event.getFormattedMessage().contains("ValueError: jpy-bridge-traceback"));
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditivity);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }

    private static String streamId(String streamName) {
        try (PyModule sys = PyModule.importModule("sys");
             var stream = sys.getAttribute(streamName);
             PyModule builtins = PyModule.importModule("builtins");
             var id = builtins.callMethod("id", stream)) {
            return id.repr();
        }
    }

    private static String required(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing system property: " + name);
        }
        return value;
    }
}
