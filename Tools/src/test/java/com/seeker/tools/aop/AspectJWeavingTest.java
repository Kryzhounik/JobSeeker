package com.seeker.tools.aop;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AspectJWeavingTest {
    @Test
    void weavesAdviceForAnnotatedMethodOnOrdinaryObject() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        String annotatedResult;
        String annotatedOutput;
        String unannotatedResult;
        String unannotatedOutput;

        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            annotatedResult = new Sample().invokeAnnotated();
            annotatedOutput = captured.toString(StandardCharsets.UTF_8);

            captured.reset();
            unannotatedResult = new Sample().invokeUnannotated();
            unannotatedOutput = captured.toString(StandardCharsets.UTF_8);
        } finally {
            System.setOut(originalOut);
        }

        assertEquals("annotated result", annotatedResult);
        assertEquals("Hello world" + System.lineSeparator(), annotatedOutput);
        assertEquals("unannotated result", unannotatedResult);
        assertEquals("", unannotatedOutput);
    }

    private static class Sample {
        String invokeAnnotated() {
            return annotatedMethod();
        }

        String invokeUnannotated() {
            return unannotatedMethod();
        }

        @HelloWorld
        private String annotatedMethod() {
            return "annotated result";
        }

        private String unannotatedMethod() {
            return "unannotated result";
        }
    }
}
