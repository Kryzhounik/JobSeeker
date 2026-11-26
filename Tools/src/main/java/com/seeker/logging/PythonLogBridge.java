package com.seeker.logging;

import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/** JPy callback carrying Python records into the existing JVM logging pipeline. */
public final class PythonLogBridge {
    private static final int PYTHON_ERROR = 40;
    private static final int PYTHON_WARNING = 30;
    private static final int PYTHON_INFO = 20;
    private static final int PYTHON_DEBUG = 10;

    public void log(String name, int level, String message, String traceback) {
        String text = traceback.isEmpty() ? message : message + "\n" + traceback;
        LoggerFactory.getLogger(name).atLevel(targetLevel(level)).log(text);
    }

    private static Level targetLevel(int level) {
        if (level >= PYTHON_ERROR) return Level.ERROR;
        if (level >= PYTHON_WARNING) return Level.WARN;
        if (level >= PYTHON_INFO) return Level.INFO;
        if (level >= PYTHON_DEBUG) return Level.DEBUG;
        return Level.TRACE;
    }
}
