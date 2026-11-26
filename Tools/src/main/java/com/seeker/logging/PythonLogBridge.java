package com.seeker.logging;

import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/** JPy callback carrying Python records into the existing JVM logging pipeline. */
public final class PythonLogBridge {
    public void log(String name, int level, String message, String traceback) {
        String text = traceback.isEmpty() ? message : message + "\n" + traceback;
        LoggerFactory.getLogger(name).atLevel(targetLevel(level)).log(text);
    }

    private static Level targetLevel(int level) {
        if (level >= 40) return Level.ERROR;
        if (level >= 30) return Level.WARN;
        if (level >= 20) return Level.INFO;
        if (level >= 10) return Level.DEBUG;
        return Level.TRACE;
    }
}
