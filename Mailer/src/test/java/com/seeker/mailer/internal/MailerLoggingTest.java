package com.seeker.mailer.internal;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.seeker.mailer.MailException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MailerLoggingTest {
    @TempDir Path root;

    @Test
    void imapAuthenticationFailureLogsControlledDetailsWithoutThrowableProxy() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        var store = new ImapMailAccessTest.StubStore();
        store.connectFailure = new jakarta.mail.AuthenticationFailedException("private provider response marker");

        try (Capture capture = capture(MailException.class)) {
            MailException failure = assertThrows(MailException.class,
                    () -> new ImapMailAccess(root, session -> store).listFolders());

            assertSame(store.connectFailure, failure.getCause());
            assertTrue(failure.getMessage().contains("authentication failed"));
            assertEquals(1, capture.events().size());
            ILoggingEvent event = capture.events().getFirst();
            String message = event.getFormattedMessage();
            assertTrue(message.contains("authentication failed"));
            assertTrue(message.contains("AuthenticationFailedException"));
            assertFalse(message.contains("private provider response marker"));
            assertNull(event.getThrowableProxy());
        }
    }

    @Test
    void interpreterLogsIdentifiersAndUrlsButNotBodyOrDecodeCauseText() throws Exception {
        try (Capture capture = capture(MessageInterpreter.class);
                Capture errors = capture(MailException.class)) {
            var interpreter = new MessageInterpreter(root);
            String bodyMarker = "private body marker";
            String raw = "From: jobs@linkedin.com\nSubject: Jobs for you\n\n"
                    + "https://www.linkedin.com/jobs/view/12345/ " + bodyMarker;
            var message = interpreter.interpret(input(raw), "fixture-message-id", null);

            assertTrue(message.vacancyUrls().contains("https://www.linkedin.com/jobs/view/12345/"));
            assertTrue(capture.text().contains("fixture-message-id"));
            assertTrue(capture.text().contains("LINKEDIN_RECOMMENDATION"));
            assertTrue(capture.text().contains("https://www.linkedin.com/jobs/view/12345/"));
            assertFalse(capture.text().contains(bodyMarker));
            capture.clear();

            IOException readFailure = new IOException("private decode cause marker");
            InputStream broken = new InputStream() {
                @Override
                public int read() throws IOException {
                    throw readFailure;
                }
            };
            MailException failure = assertThrows(MailException.class,
                    () -> interpreter.interpret(broken, "broken-message-id", null));

            assertNotNull(failure.getCause());
            assertTrue(errors.text().contains("Cannot decode email message"));
            assertTrue(capture.text().contains("broken-message-id"));
            assertFalse(errors.text().contains("private decode cause marker"));
            assertTrue(errors.events().stream().allMatch(event -> event.getThrowableProxy() == null));
        }
    }

    @Test
    void blockedLegacyMailerLogPathDoesNotPreventSuccessfulFolderListing() throws Exception {
        MailSettingsTest.writeConfig(root, MailSettingsTest.CONFIG);
        Files.createDirectories(root.resolve("Data/mailer/mailer.log"));
        var store = new ImapMailAccessTest.StubStore();

        try (Capture capture = capture(ImapMailAccess.class)) {
            assertEquals(List.of(), new ImapMailAccess(root, session -> store).listFolders());

            assertTrue(store.closed);
            assertFalse(capture.events().isEmpty());
        }
    }

    private static InputStream input(String value) {
        return new java.io.ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Capture capture(Class<?> loggerClass) {
        return new Capture(loggerClass);
    }

    private static class Capture implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousAdditivity;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private Capture(Class<?> loggerClass) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            logger = context.getLogger(loggerClass);
            previousLevel = logger.getLevel();
            previousAdditivity = logger.isAdditive();
            appender.setContext(context);
            appender.start();
            logger.setLevel(Level.TRACE);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        private List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        private String text() {
            return String.join("\n", appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList());
        }

        private void clear() {
            appender.list.clear();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            logger.setAdditive(previousAdditivity);
            appender.stop();
        }
    }
}
