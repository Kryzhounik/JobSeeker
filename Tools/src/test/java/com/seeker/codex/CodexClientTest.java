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
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return CodexClient.call(List.of(java, "-cp", System.getProperty("java.class.path"),
                ProxyProcess.class.getName(), Base64.getEncoder().encodeToString(response.getBytes(StandardCharsets.UTF_8)),
                Integer.toString(exitCode)), Path.of("."));
    }

    public static class ProxyProcess {
        public static void main(String[] args) {
            if (!"--json-result".equals(args[2])) throw new IllegalArgumentException("Missing response mode");
            System.out.println(new String(Base64.getDecoder().decode(args[0]), StandardCharsets.UTF_8));
            System.exit(Integer.parseInt(args[1]));
        }
    }
}
