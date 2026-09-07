package com.seeker.collector.linkedin.cli;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliOptionsTest {
    @Test
    void parsesBatchOverrides() {
        CliOptions options = CliOptions.parse(new String[]{
                "batch",
                "--limit", "7",
                "--location", "Moldova:106178099",
                "--run-id", "run-1",
                "--headless"
        });

        assertEquals(CliOptions.Command.BATCH, options.command());
        assertEquals(7, options.limit());
        assertEquals("Moldova:106178099", options.location());
        assertEquals("run-1", options.runId());
        assertTrue(options.headless());
    }

    @Test
    void fromUrlRequiresUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"from-url"})
        );
    }
}
