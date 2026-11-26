package com.seeker.logging;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.slf4j.event.Level;

/** Requests a log entry after successful method execution. */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogOutput {
    String message() default "";

    Level level() default Level.INFO;

    /** Result property names or SpEL expressions using {@code #result}; empty selects no values. */
    String[] include() default {};

    /** Optional boolean SpEL expression; empty means no additional condition. */
    String condition() default "";

    /** Whether to include the elapsed method execution time. */
    boolean duration() default false;
}
