package com.seeker.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/**
 * Requests a log entry when method execution throws a matching exception.
 * Exceptions whose concrete class carries {@link LogExceptionCreation} are
 * excluded because their creation is already responsible for logging.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogException {
    /** Custom message; empty uses the method name followed by {@code exception}. */
    String message() default "";

    Level level() default Level.ERROR;

    /** Matches the listed exception types and their subclasses. */
    Class<? extends Throwable>[] types() default {Throwable.class};
}
