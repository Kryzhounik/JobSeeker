package com.seeker.logging;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

/** Shared declarative logging, woven into ordinary Java objects by AspectJ. */
@Aspect
public class LoggingAspect {
    private record Entry(Level level, String message, String[] include, String condition,
                         boolean output, Long elapsedNanos, Throwable failure) { }

    @AfterReturning(value = "initialization(java.lang.Throwable+.new(..)) && @within(annotation) && this(failure)",
            argNames = "initialization,annotation,failure")
    public void logExceptionCreation(JoinPoint initialization, LogExceptionCreation annotation, Throwable failure) {
        Class<?> type = initialization.getSignature().getDeclaringType();
        // A superclass initialization must not log a partially constructed subclass.
        if (type != failure.getClass()) return;
        Logger logger = LoggerFactory.getLogger(type);
        if (!logger.isEnabledForLevel(annotation.level())) return;
        logger.atLevel(annotation.level()).setCause(failure).log(type.getSimpleName() + " created");
    }

    @Around("execution(@com.seeker.logging.LogInput * *(..)) || "
            + "execution(@com.seeker.logging.LogOutput * *(..)) || "
            + "execution(@com.seeker.logging.LogException * *(..))")
    public Object logExecution(ProceedingJoinPoint invocation) throws Throwable {
        Method method = ((MethodSignature) invocation.getSignature()).getMethod();
        Logger logger = LoggerFactory.getLogger(method.getDeclaringClass());
        Object[] arguments = invocation.getArgs();
        LogInput input = method.getAnnotation(LogInput.class);
        LogOutput output = method.getAnnotation(LogOutput.class);
        LogException exception = method.getAnnotation(LogException.class);
        if (input != null) {
            write(logger, method, arguments, null, new Entry(input.level(), message(method, input.message(), "input"),
                    input.include(), input.condition(), false, null, null));
        }
        boolean timed = output != null && output.duration() && logger.isEnabledForLevel(output.level());
        long started = timed ? System.nanoTime() : 0;
        Object result;
        try {
            result = invocation.proceed();
        } catch (Throwable failure) {
            logException(logger, method, arguments, exception, failure);
            throw failure;
        }
        Long elapsed = timed ? System.nanoTime() - started : null;
        if (output != null) {
            write(logger, method, arguments, result, new Entry(output.level(), message(method, output.message(), "output"),
                    output.include(), output.condition(), true, elapsed, null));
        }
        return result;
    }

    private static void logException(Logger logger, Method method, Object[] arguments,
                                     LogException annotation, Throwable failure) {
        if (annotation == null || Arrays.stream(annotation.types()).noneMatch(type -> type.isInstance(failure))) return;
        write(logger, method, arguments, null, new Entry(annotation.level(), message(method, annotation.message(), "exception"),
                new String[0], "", false, null, failure));
    }

    private static String message(Method method, String message, String event) {
        return message.isEmpty() ? method.getName() + " " + event : message;
    }

    private static void write(Logger logger, Method method, Object[] arguments, Object result, Entry entry) {
        if (!logger.isEnabledForLevel(entry.level())) return;
        String expression = entry.condition();
        try {
            var evaluator = new LogExpressionEvaluator(method, arguments, result, entry.output());
            if (!entry.condition().isEmpty() && !evaluator.matches(entry.condition())) return;
            var values = new LinkedHashMap<String, Object>();
            for (String selection : entry.include()) {
                expression = selection;
                values.put(selection, evaluator.select(selection));
            }
            StringBuilder message = new StringBuilder(entry.message());
            if (!values.isEmpty()) message.append(' ').append(values);
            if (entry.elapsedNanos() != null) message.append(" duration_ms=").append(entry.elapsedNanos() / 1_000_000);
            logger.atLevel(entry.level()).setCause(entry.failure()).log(message.toString());
        } catch (RuntimeException error) {
            logger.warn("Logging expression failed: method={} expression={} reason={}",
                    method.toGenericString(), expression, error.toString());
        }
    }
}
