package com.seeker.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogExceptionCreationTest {
    @Test
    void logsInitializedExceptionAtErrorWithInstanceAsCause() {
        try (Capture capture = capture(ReadyException.class)) {
            ReadyException exception = new ReadyException("ready");

            assertEquals("ready", exception.detail);
            assertEquals(1, capture.events().size());
            ILoggingEvent event = capture.events().getFirst();
            assertEquals(Level.ERROR, event.getLevel());
            assertEquals("ReadyException created", event.getFormattedMessage());
            assertEquals("ready", event.getThrowableProxy().getMessage());
            assertSame(exception, ((ThrowableProxy) event.getThrowableProxy()).getThrowable());
        }
    }

    @Test
    void honorsWarnOverrideAndLogsOnceAfterThisChainingCompletes() {
        try (Capture capture = capture(ChainedException.class)) {
            ChainedException exception = new ChainedException("final");

            assertEquals("final", exception.detail);
            assertEquals(1, capture.events().size());
            assertEquals(Level.WARN, capture.events().getFirst().getLevel());
            assertEquals("ChainedException created", capture.events().getFirst().getFormattedMessage());
            assertEquals("final", capture.events().getFirst().getThrowableProxy().getMessage());
            assertSame(exception, ((ThrowableProxy) capture.events().getFirst().getThrowableProxy()).getThrowable());
        }
    }

    @Test
    void doesNotLogFailedOrUnannotatedConstructionOrUnannotatedSubclass() {
        try (Capture capture = capture(FailedException.class)) {
            IllegalStateException thrown = assertThrows(IllegalStateException.class, FailedException::new);
            assertEquals("construction failed", thrown.getMessage());
            assertTrue(capture.events().isEmpty());
        }

        try (Capture capture = capture(PlainException.class)) {
            new PlainException();
            assertTrue(capture.events().isEmpty());
        }

        try (Capture baseCapture = capture(BaseException.class);
                Capture childCapture = capture(UnannotatedChildException.class)) {
            UnannotatedChildException child = new UnannotatedChildException();
            assertEquals("child initialized", child.detail);
            assertTrue(baseCapture.events().isEmpty());
            assertTrue(childCapture.events().isEmpty());
        }
    }

    @Test
    void disabledLevelEmitsNothing() {
        try (Capture capture = capture(DisabledException.class)) {
            capture.level(Level.OFF);
            new DisabledException();

            assertTrue(capture.events().isEmpty());
        }
    }

    private static Capture capture(Class<?> exceptionClass) {
        return new Capture(exceptionClass);
    }

    @LogExceptionCreation
    static class ReadyException extends RuntimeException {
        final String detail;

        ReadyException(String detail) {
            this.detail = detail;
        }

        @Override
        public String getMessage() {
            return detail;
        }
    }

    @LogExceptionCreation(level = org.slf4j.event.Level.WARN)
    static class ChainedException extends RuntimeException {
        private String detail;

        ChainedException(String detail) {
            this();
            this.detail = detail;
        }

        private ChainedException() {
        }

        @Override
        public String getMessage() {
            return detail;
        }
    }

    @LogExceptionCreation
    static class FailedException extends RuntimeException {
        FailedException() {
            throw new IllegalStateException("construction failed");
        }
    }

    static class PlainException extends RuntimeException {
        PlainException() {
        }
    }

    @LogExceptionCreation
    static class BaseException extends RuntimeException {
        BaseException() {
        }
    }

    static class UnannotatedChildException extends BaseException {
        String detail;

        UnannotatedChildException() {
            detail = "child initialized";
        }
    }

    @LogExceptionCreation
    static class DisabledException extends RuntimeException {
        DisabledException() {
        }
    }

    private static class Capture implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousAdditivity;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private Capture(Class<?> exceptionClass) {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            logger = context.getLogger(exceptionClass);
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

        private void level(Level level) {
            logger.setLevel(level);
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
