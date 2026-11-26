package com.seeker.logging;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/** Evaluation state belongs to one invocation, including concurrent method calls. */
final class LogExpressionEvaluator {
    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private final SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();
    private final Map<String, Object> arguments = new LinkedHashMap<>();
    private final Object result;
    private final boolean output;

    LogExpressionEvaluator(Method method, Object[] values, Object result, boolean output) {
        var parameters = method.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            String name = parameters[index].getName();
            arguments.put(name, values[index]);
            context.setVariable(name, values[index]);
        }
        this.result = result;
        this.output = output;
        if (output) context.setVariable("result", result);
    }

    boolean matches(String condition) {
        return Boolean.TRUE.equals(PARSER.parseExpression(condition).getValue(context, Boolean.class));
    }

    Object select(String selection) {
        if (!output && arguments.containsKey(selection)) return arguments.get(selection);
        return PARSER.parseExpression(selection).getValue(context, result);
    }
}
