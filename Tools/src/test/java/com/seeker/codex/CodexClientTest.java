package com.seeker.codex;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodexClientTest {
    @Test
    void returnsTheAgentPayloadWithoutInterpretingIt() throws Exception {
        assertEquals("{\"nonrelevant_job_ids\":[]}", call(
                "{\"result\":\"{\\\"nonrelevant_job_ids\\\":[]}\"}", 0));
    }

    @Test
    void processTransportThrowsCheckedLimitWithResetTime() {
        UsageLimitExceededException error = assertThrows(UsageLimitExceededException.class,
                () -> call("""
                        {"error":{"code":"usage_limit","message":"Quota exhausted",
                        "retry_at":"2026-10-22T12:20:00+00:00"}}
                        """, 1));
        assertEquals("Quota exhausted", error.getMessage());
        assertEquals(Instant.parse("2026-10-22T12:20:00Z"), error.getRetryAt());
    }

    @Test
    void stderrDoesNotCorruptSuccessfulJsonOrBlockTheProcess() throws Exception {
        assertEquals("ok", call("{\"result\":\"ok\"}", 0, "warning\n".repeat(1024)));
    }

    @Test
    void stderrDoesNotHideUsageLimitOrResetTime() {
        UsageLimitExceededException error = assertThrows(UsageLimitExceededException.class,
                () -> call("""
                        {"error":{"code":"usage_limit","message":"Quota exhausted",
                        "retry_at":"2026-10-22T12:20:00Z"}}
                        """, 1, "Python warning"));
        assertEquals("Quota exhausted", error.getMessage());
        assertEquals(Instant.parse("2026-10-22T12:20:00Z"), error.getRetryAt());
    }

    @Test
    void invalidJsonIncludesStderrDiagnostics() {
        IOException error = assertThrows(IOException.class,
                () -> call("not json", 1, "Python traceback"));
        assertTrue(error.getMessage().contains("Invalid Codex Proxy response (1)"));
        assertTrue(error.getMessage().contains("stderr: Python traceback"));
    }

    @Test
    void structuredFailureIncludesStderrDiagnostics() {
        IOException error = assertThrows(IOException.class,
                () -> call("{\"error\":{\"code\":\"codex_error\",\"message\":\"Invalid model\"}}",
                        1, "Python warning"));
        assertEquals("Codex Proxy failed (1): Invalid model\nstderr: Python warning", error.getMessage());
    }

    @Test
    void missingResetTimeDoesNotHideTheLimit() {
        UsageLimitExceededException error = assertThrows(UsageLimitExceededException.class,
                () -> call("{\"error\":{\"code\":\"usage_limit\",\"message\":\"Quota exhausted\"}}", 1));
        assertNull(error.getRetryAt());
    }

    @Test
    void otherFailuresRemainOrdinaryTransportErrors() {
        IOException error = assertThrows(IOException.class,
                () -> call("{\"error\":{\"code\":\"codex_error\",\"message\":\"Invalid model\"}}", 1));
        assertEquals("Codex Proxy failed (1): Invalid model", error.getMessage());
    }

    private String call(String response, int exitCode) throws Exception {
        return call(response, exitCode, "");
    }

    private String call(String response, int exitCode, String stderr) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return CodexClient.call(List.of(java, "-cp", System.getProperty("java.class.path"),
                ProxyProcess.class.getName(), Base64.getEncoder().encodeToString(response.getBytes(StandardCharsets.UTF_8)),
                Integer.toString(exitCode),
                Base64.getEncoder().encodeToString(stderr.getBytes(StandardCharsets.UTF_8))), Path.of("."));
    }

    public static class ProxyProcess {
        public static void main(String[] args) {
            if (!"--json-result".equals(args[3])) throw new IllegalArgumentException("Missing response mode");
            System.err.print(new String(Base64.getDecoder().decode(args[2]), StandardCharsets.UTF_8));
            System.out.println(new String(Base64.getDecoder().decode(args[0]), StandardCharsets.UTF_8));
            System.exit(Integer.parseInt(args[1]));
        }
    }
}
