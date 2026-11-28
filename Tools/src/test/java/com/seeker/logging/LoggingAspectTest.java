package com.seeker.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoggingAspectTest {
    private static final Pattern DURATION = Pattern.compile("duration_ms=(\\d+)");

    @Test
    void interceptsPrivateSelfInvocationAndStaticAnnotatedExecutionOnce() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();

            assertEquals("private-value", fixture.invokePrivate("private-value"));
            assertEquals("static-value", Fixture.staticAnnotated("static-value"));

            assertEquals(1, fixture.privateCalls);
            assertEquals(1, capture.count("private-input"));
            assertEquals(1, capture.count("static-input"));
            assertEquals(2, capture.events().size());
            assertTrue(capture.text().contains("{value=private-value}"));
            assertTrue(capture.text().contains("{value=static-value}"));
        }
    }

    @Test
    void selectsSimpleInputRecordResultAndArgumentExpressionsIncludingVoidOutput() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();
            assertEquals("input-value", fixture.simpleInput("input-value"));
            OutputRecord result = fixture.recordResult("ready");
            fixture.argumentResult("argument-value");
            fixture.voidResult("void-argument");

            assertEquals("ready", result.status());
            assertEquals(1, capture.count("simple-input"));
            assertEquals(1, capture.count("record-output"));
            assertEquals(1, capture.count("argument-output"));
            assertEquals(1, capture.count("void-output"));
            assertTrue(capture.text().contains("{value=input-value}"));
            assertTrue(capture.text().contains("{status=ready}"));
            assertTrue(capture.text().contains("{#value=argument-value}"));
            assertTrue(capture.text().contains("{#value=void-argument}"));
        }
    }

    @Test
    void evaluatesNestedArgumentAndResultPropertiesAndSkipsFalseCondition() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();
            InputRecord request = new InputRecord("nested-name");

            fixture.nestedInput(request);
            OutputRecord result = fixture.nestedOutput(request);
            assertEquals("nested-name-result", result.status());
            assertEquals("condition-result", fixture.conditional(false, "condition-value"));

            assertEquals(1, capture.count("nested-input"));
            assertEquals(1, capture.count("nested-output"));
            assertEquals(0, capture.count("conditional-input"));
            assertTrue(capture.text().contains("#request.name=nested-name"));
            assertTrue(capture.text().contains("#result.status=nested-name-result"));
            assertEquals(3, fixture.bodyCalls);
        }
    }

    @Test
    void logsInputAndOutputWithNonnegativeDurationAndReturnsSameValue() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();
            OutputRecord value = new OutputRecord("timed-value");

            assertSame(value, fixture.timed(7, value));

            assertEquals(1, capture.count("timed-input"));
            assertEquals(1, capture.count("timed-output"));
            Matcher duration = DURATION.matcher(capture.messageFor("timed-output"));
            assertTrue(duration.find());
            assertTrue(Long.parseLong(duration.group(1)) >= 0);
            assertTrue(capture.messageFor("timed-output").contains("{status=timed-value}"));
        }
    }

    @Test
    void disabledLevelSkipsInvalidExpressionsAndPropertyAccessors() {
        try (Capture capture = capture()) {
            capture.level(Level.INFO);
            Fixture fixture = new Fixture();
            Probe probe = new Probe();

            assertSame(probe, fixture.disabled(probe));

            assertEquals(1, fixture.bodyCalls);
            assertEquals(0, probe.accesses);
            assertTrue(capture.events().isEmpty());
        }
    }

    @Test
    void logsMatchedExceptionSubclassesAndRethrowsIdentityWithoutOutput() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();
            SpecialArgumentException matched = new SpecialArgumentException("matched");

            SpecialArgumentException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    SpecialArgumentException.class, () -> fixture.matchedFailure(matched));

            assertSame(matched, thrown);
            assertEquals(1, capture.events().size());
            ILoggingEvent event = capture.events().getFirst();
            assertEquals("matched-failure", event.getFormattedMessage());
            assertSame(matched, ((ThrowableProxy) event.getThrowableProxy()).getThrowable());

            capture.clear();
            IOException unmatched = new IOException("unmatched");
            IOException rethrown = org.junit.jupiter.api.Assertions.assertThrows(
                    IOException.class, () -> fixture.unmatchedFailure(unmatched));

            assertSame(unmatched, rethrown);
            assertTrue(capture.events().isEmpty());
        }
    }

    @Test
    void expressionFailuresWarnWithoutArgumentsAndDoNotReplaceBodyResultsOrFailures() {
        try (Capture capture = capture()) {
            Fixture fixture = new Fixture();
            String secret = "private-argument-marker";
            RuntimeException original = new IllegalStateException("body-failure");

            assertEquals("input-result", fixture.invalidInput(secret));
            assertEquals("output-result", fixture.invalidOutput(secret));
            RuntimeException thrown = org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalStateException.class, () -> fixture.invalidInputFailure(secret, original));

            assertSame(original, thrown);
            assertEquals(3, fixture.bodyCalls);
            assertEquals(3, capture.events().size());
            assertTrue(capture.text().contains("method="));
            assertTrue(capture.text().contains("expression=#missing.value"));
            assertTrue(capture.text().contains("reason="));
            assertTrue(capture.text().contains("invalidInput"));
            assertTrue(capture.text().contains("invalidOutput"));
            assertTrue(capture.text().contains("invalidInputFailure"));
            assertFalse(capture.text().contains(secret));
        }
    }

    private static Capture capture() {
        return new Capture();
    }

    public static class Fixture {
        private int privateCalls;
        private int bodyCalls;

        String invokePrivate(String value) {
            return privateAnnotated(value);
        }

        @LogInput(message = "private-input", include = "value")
        private String privateAnnotated(String value) {
            privateCalls++;
            return value;
        }

        @LogInput(message = "static-input", include = "value")
        public static String staticAnnotated(String value) {
            return value;
        }

        @LogInput(message = "simple-input", include = "value")
        public String simpleInput(String value) {
            bodyCalls++;
            return value;
        }

        @LogOutput(message = "record-output", include = "status")
        public OutputRecord recordResult(String value) {
            return new OutputRecord(value);
        }

        @LogOutput(message = "argument-output", include = "#value")
        public String argumentResult(String value) {
            return value;
        }

        @LogOutput(message = "void-output", include = "#value")
        public void voidResult(String value) {
            bodyCalls++;
        }

        @LogInput(message = "nested-input", include = "#request.name")
        public void nestedInput(InputRecord request) {
            bodyCalls++;
        }

        @LogOutput(message = "nested-output", include = {"#request.name", "#result.status"})
        public OutputRecord nestedOutput(InputRecord request) {
            bodyCalls++;
            return new OutputRecord(request.name() + "-result");
        }

        @LogInput(message = "conditional-input", include = "value", condition = "#enabled")
        public String conditional(boolean enabled, String value) {
            bodyCalls++;
            return "condition-result";
        }

        @LogInput(message = "timed-input", include = "amount")
        @LogOutput(message = "timed-output", include = "status", duration = true)
        public OutputRecord timed(int amount, OutputRecord value) {
            return value;
        }

        @LogInput(level = org.slf4j.event.Level.DEBUG, include = "#probe.value", condition = "(")
        @LogOutput(level = org.slf4j.event.Level.DEBUG, include = "#result.value", condition = "(")
        public Probe disabled(Probe probe) {
            bodyCalls++;
            return probe;
        }

        @LogException(message = "matched-failure", types = IllegalArgumentException.class)
        @LogOutput(message = "must-not-log-output")
        public String matchedFailure(IllegalArgumentException failure) {
            throw failure;
        }

        @LogException(message = "unmatched-failure", types = IllegalArgumentException.class)
        public void unmatchedFailure(IOException failure) throws IOException {
            throw failure;
        }

        @LogInput(message = "invalid-input", include = "#missing.value")
        public String invalidInput(String secret) {
            bodyCalls++;
            return "input-result";
        }

        @LogOutput(message = "invalid-output", include = "#missing.value")
        public String invalidOutput(String secret) {
            bodyCalls++;
            return "output-result";
        }

        @LogInput(message = "invalid-input-failure", include = "#missing.value")
        public String invalidInputFailure(String secret, RuntimeException failure) {
            bodyCalls++;
            throw failure;
        }
    }

    record InputRecord(String name) {
    }

    public record OutputRecord(String status) {
    }

    public static class Probe {
        private int accesses;

        public String getValue() {
            accesses++;
            return "probe-value";
        }
    }

    private static final class Capture implements AutoCloseable {
        private final Logger logger;
        private final Level previousLevel;
        private final boolean previousAdditivity;
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private Capture() {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            logger = context.getLogger(Fixture.class);
            previousLevel = logger.getLevel();
            previousAdditivity = logger.isAdditive();
            appender.setContext(context);
            appender.start();
            logger.setLevel(Level.TRACE);
            logger.setAdditive(false);
            logger.addAppender(appender);
        }

        private void level(Level level) {
            logger.setLevel(level);
        }

        private List<ILoggingEvent> events() {
            return List.copyOf(appender.list);
        }

        private long count(String fragment) {
            return appender.list.stream().filter(event -> event.getFormattedMessage().contains(fragment)).count();
        }

        private String messageFor(String fragment) {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(message -> message.contains(fragment)).findFirst().orElseThrow();
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

    private static final class SpecialArgumentException extends IllegalArgumentException {
        private SpecialArgumentException(String message) {
            super(message);
        }
    }
}
