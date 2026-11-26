package com.seeker.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/** Requests a log entry before method execution. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogInput {
    String message() default "";

    Level level() default Level.INFO;

    /** Argument names or SpEL expressions selecting values; empty selects no arguments. */
    String[] include() default {};

    /** Optional boolean SpEL expression; empty means no additional condition. */
    String condition() default "";
}
