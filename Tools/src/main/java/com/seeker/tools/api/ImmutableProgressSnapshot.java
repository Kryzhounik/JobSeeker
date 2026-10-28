package com.seeker.tools.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable value implementation without module-specific calculations. */
public record ImmutableProgressSnapshot(
        String runId,
        String stage,
        String action,
        String progress,
        Map<String, String> fields,
        String details
) implements ProgressSnapshot {
    public ImmutableProgressSnapshot {
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
}
