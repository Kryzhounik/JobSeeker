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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void safeModeLogsControlledFailureAndCauseTypeWithoutThrowableDetails() {
        try (Capture capture = capture(SafeFailure.class);
                Capture boundaryCapture = capture(SafeFailureFixture.class)) {
            IllegalArgumentException cause = new IllegalArgumentException("private provider response marker");
            cause.addSuppressed(new IllegalStateException("suppressed private marker"));
            SafeFailure failure = new SafeFailure("controlled top-level message", cause);

            SafeFailure thrown = assertThrows(SafeFailure.class,
                    () -> new SafeFailureFixture().fail(failure));

            assertSame(failure, thrown);
            assertSame(cause, thrown.getCause());
            assertEquals(1, capture.events().size());
            ILoggingEvent event = capture.events().getFirst();
            String message = event.getFormattedMessage();
            assertTrue(message.contains("controlled top-level message"));
            assertTrue(message.contains("IllegalArgumentException"));
            assertFalse(message.contains("private provider response marker"));
            assertFalse(message.contains("suppressed private marker"));
            assertNull(event.getThrowableProxy());
            assertTrue(boundaryCapture.events().isEmpty());
        }
    }

    @Test
    void suppressesBoundaryRecordsForCreationLoggedExceptionAcrossNestedMethods() {
        try (Capture creationCapture = capture(ChainedException.class);
                Capture boundaryCapture = capture(BoundaryFixture.class)) {
            ChainedException thrown = assertThrows(
                    ChainedException.class, () -> new BoundaryFixture().outer());

            assertEquals(1, creationCapture.events().size());
            ILoggingEvent creation = creationCapture.events().getFirst();
            assertEquals(Level.WARN, creation.getLevel());
            assertEquals("ChainedException created", creation.getFormattedMessage());
            assertSame(thrown, ((ThrowableProxy) creation.getThrowableProxy()).getThrowable());
            assertTrue(boundaryCapture.events().isEmpty());
        }
    }

    @Test
    void logsUnannotatedSubclassAtAnnotatedMethodBoundary() {
        try (Capture creationCapture = capture(UnannotatedChildException.class);
                Capture boundaryCapture = capture(BoundaryFixture.class)) {
            UnannotatedChildException thrown = assertThrows(
                    UnannotatedChildException.class, () -> new BoundaryFixture().childFailure());

            assertTrue(creationCapture.events().isEmpty());
            assertEquals(1, boundaryCapture.events().size());
            ILoggingEvent boundary = boundaryCapture.events().getFirst();
            assertEquals(Level.ERROR, boundary.getLevel());
            assertEquals("child-boundary", boundary.getFormattedMessage());
            assertSame(thrown, ((ThrowableProxy) boundary.getThrowableProxy()).getThrowable());
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

    @LogExceptionCreation(stackTrace = false)
    static class SafeFailure extends RuntimeException {
        SafeFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static class SafeFailureFixture {
        @LogException
        void fail(SafeFailure failure) {
            throw failure;
        }
    }

    static class BoundaryFixture {
        @LogException(message = "outer-boundary", level = org.slf4j.event.Level.ERROR)
        void outer() {
            inner();
        }

        @LogException(message = "inner-boundary", level = org.slf4j.event.Level.ERROR)
        private void inner() {
            throw new ChainedException("boundary");
        }

        @LogException(message = "child-boundary", level = org.slf4j.event.Level.ERROR)
        void childFailure() {
            throw new UnannotatedChildException();
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
