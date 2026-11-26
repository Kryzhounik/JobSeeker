package com.seeker.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonLogBridgeTest {
    @Test
    void mapsPythonLevelsAndForwardsMultilineTracebackOnce() {
        String name = "com.seeker.logging.python-bridge-test";
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(name);
        boolean originalAdditivity = logger.isAdditive();
        Level originalLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.setContext(context);
        appender.start();
        logger.setAdditive(false);
        logger.setLevel(Level.TRACE);
        logger.addAppender(appender);

        try {
            PythonLogBridge bridge = new PythonLogBridge();
            bridge.log(name, 10, "python-debug", "");
            bridge.log(name, 20, "python-info", "");
            bridge.log(name, 30, "python-warning", "");
            bridge.log(name, 40, "python failure",
                    "Traceback (most recent call last):\nValueError: fixture");
            bridge.log(name, 50, "python-critical", "");

            assertEquals(5, appender.list.size());
            assertEquals(java.util.List.of(Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR, Level.ERROR),
                    appender.list.stream().map(ILoggingEvent::getLevel).toList());
            for (ILoggingEvent event : appender.list) {
                assertEquals(name, event.getLoggerName());
            }
            assertEquals("python failure\nTraceback (most recent call last):\nValueError: fixture",
                    appender.list.get(3).getFormattedMessage());
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(originalAdditivity);
            logger.setLevel(originalLevel);
            appender.stop();
        }
    }
}
