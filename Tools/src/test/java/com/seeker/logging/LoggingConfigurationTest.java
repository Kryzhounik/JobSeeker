package com.seeker.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.core.FileAppender;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.SLF4JServiceProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingConfigurationTest {
    private static final String LOGGER_NAME = "com.seeker.logging.configuration-test";

    @TempDir
    Path temporaryDirectory;

    @Test
    void sharedConfigurationsWriteMatchingEventsToFileAndStderrOnly() throws Exception {
        var providers = ServiceLoader.load(SLF4JServiceProvider.class).stream().toList();
        assertEquals(1, providers.size());
        assertEquals("ch.qos.logback.classic.spi.LogbackServiceProvider", providers.getFirst().type().getName());

        var captured = captureStreams();
        LoggerContext context = new LoggerContext();
        try {
            configure(context, temporaryDirectory);
            Path logFile = configuredLogFile(context);
            Logger logger = context.getLogger(LOGGER_NAME);
            IllegalArgumentException failure = new IllegalArgumentException("expected failure");
            logger.info("logging-info-event");
            logger.error("logging-error-event", failure);
            context.stop();

            String stderr = captured.stderr();
            String file = Files.readString(logFile, StandardCharsets.UTF_8);
            assertEquals(stderr, file);
            assertEquals(1, occurrences(stderr, "logging-info-event"));
            assertEquals(1, occurrences(stderr, "logging-error-event"));
            for (String output : List.of(stderr, file)) {
                assertTrue(output.contains("INFO  " + LOGGER_NAME + " - logging-info-event"));
                assertTrue(output.contains("ERROR " + LOGGER_NAME + " - logging-error-event"));
                assertTrue(output.contains("java.lang.IllegalArgumentException: expected failure"));
                assertTrue(output.contains("at "));
            }
            assertEquals("", captured.stdout());
        } finally {
            context.stop();
            captured.restore();
        }
    }

    @Test
    void explicitRootWinsOverPropertiesAndWorkingDirectoryAndInitializationIsIdempotent() throws Exception {
        String classPath = System.getProperty("java.class.path");
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Path explicitRoot = temporaryDirectory.resolve("explicit-root");
        Path conflictingRoot = temporaryDirectory.resolve("conflicting-root");
        Files.createDirectories(conflictingRoot);
        String consoleConfig = LoggingConfigurationTest.class.getResource("/logback.xml").toExternalForm();

        ProbeResult first = runProbe(java, classPath, explicitRoot, conflictingRoot, consoleConfig, "probe-marker");
        ProbeResult second = runProbe(java, classPath, explicitRoot, conflictingRoot, consoleConfig, "probe-marker");

        assertNotEquals(first.logFile(), second.logFile());
        for (ProbeResult result : List.of(first, second)) {
            assertEquals(1, result.stdout().lines().count());
            assertEquals(result.logFile().toString(), result.stdout().trim());
            assertTrue(result.logFile().startsWith(explicitRoot.toAbsolutePath().normalize().resolve("Data/logs")));
            assertTrue(Files.isRegularFile(result.logFile()));
            assertEquals(1, occurrences(result.stderr(), "probe-marker-first"));
            assertEquals(1, occurrences(result.stderr(), "probe-marker-second"));
            String file = Files.readString(result.logFile(), StandardCharsets.UTF_8);
            assertEquals(1, occurrences(file, "probe-marker-first"));
            assertEquals(1, occurrences(file, "probe-marker-second"));
        }
        assertFalse(Files.exists(conflictingRoot.resolve("Data/logs")));
    }

    private static void configure(LoggerContext context, Path root) throws Exception {
        context.reset();
        context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
        context.putProperty("seeker.logging.root", root.toAbsolutePath().normalize().toString());
        var configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(LoggingConfigurationTest.class.getResource("/logback.xml"));
        var fileConfigurator = new ch.qos.logback.classic.joran.JoranConfigurator();
        fileConfigurator.setContext(context);
        fileConfigurator.doConfigure(LoggingConfigurationTest.class.getResource("/seeker-log-file.xml"));
    }

    private static int occurrences(String output, String text) {
        int count = 0;
        int offset = 0;
        while ((offset = output.indexOf(text, offset)) >= 0) {
            count++;
            offset += text.length();
        }
        return count;
    }

    private static Path configuredLogFile(LoggerContext context) {
        var fileAppender = (FileAppender<?>) context.getLogger(Logger.ROOT_LOGGER_NAME).getAppender("FILE");
        return Path.of(fileAppender.getFile());
    }

    private static ProbeResult runProbe(
            String java,
            String classPath,
            Path explicitRoot,
            Path conflictingRoot,
            String consoleConfig,
            String loggerName) throws Exception {
        Process process = new ProcessBuilder(
                java,
                "-Dseeker.projectRoot=" + conflictingRoot,
                "-Dseeker.project.root=" + conflictingRoot,
                "-cp",
                classPath,
                LoggingConfigurationTest.ProcessProbe.class.getName(),
                explicitRoot.toString(),
                consoleConfig,
                loggerName)
                .directory(conflictingRoot.toFile())
                .start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), stderr);
        return new ProbeResult(stdout, stderr, Path.of(stdout.trim()));
    }

    private static CapturedStreams captureStreams() {
        return new CapturedStreams();
    }

    private record ProbeResult(String stdout, String stderr, Path logFile) {
    }

    private static final class CapturedStreams {
        private final PrintStream originalOut = System.out;
        private final PrintStream originalErr = System.err;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        private CapturedStreams() {
            System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        }

        private String stdout() {
            System.out.flush();
            return stdout.toString(StandardCharsets.UTF_8);
        }

        private String stderr() {
            System.err.flush();
            return stderr.toString(StandardCharsets.UTF_8);
        }

        private void restore() {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /** Child JVM entry point; stdout carries only the initialized file path. */
    public static final class ProcessProbe {
        private ProcessProbe() {
        }

        @SuppressWarnings("PMD.SystemPrintln")
        public static void main(String[] args) throws Exception {
            Path explicitRoot = Path.of(args[0]);
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            context.reset();
            context.setMDCAdapter(new ch.qos.logback.classic.util.LogbackMDCAdapter());
            var configurator = new ch.qos.logback.classic.joran.JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(java.net.URI.create(args[1]).toURL());

            ApplicationLogging.initialize(explicitRoot.resolve("ignored/.."));
            ApplicationLogging.initialize(explicitRoot.toAbsolutePath().normalize());
            var first = LoggerFactory.getLogger(LOGGER_NAME);
            var second = LoggerFactory.getLogger(LOGGER_NAME);
            if (first != second) {
                throw new IllegalStateException("LoggerFactory did not reuse the logger");
            }
            if (first.isInfoEnabled()) {
                first.info("{}-first", args[2]);
            }
            if (second.isInfoEnabled()) {
                second.info("{}-second", args[2]);
            }

            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            int count = 0;
            for (var appenders = root.iteratorForAppenders(); appenders.hasNext();) {
                appenders.next();
                count++;
            }
            if (count != 2) {
                throw new IllegalStateException("Expected stderr and file appenders, found " + count);
            }
            Path logFile = configuredLogFile(context);
            context.stop();
            System.out.println(logFile.toAbsolutePath().normalize());
        }
    }
}
