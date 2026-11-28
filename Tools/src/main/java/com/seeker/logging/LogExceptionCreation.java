package com.seeker.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/**
 * Logs an exception after successful construction, even if it is never thrown.
 * Applies only to concrete exception classes annotated directly. The entry uses
 * the exception's class logger and includes the exception as its cause by default.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LogExceptionCreation {
    Level level() default Level.ERROR;

    /**
     * When false, logs only the exception message and direct cause type, without
     * a throwable or stack trace. Use only where the exception message is controlled.
     */
    boolean stackTrace() default true;
}
