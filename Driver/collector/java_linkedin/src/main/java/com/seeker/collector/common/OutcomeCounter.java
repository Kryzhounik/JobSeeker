package com.seeker.collector.common;

import java.util.LinkedHashMap;
import java.util.Map;

public final class OutcomeCounter {
    private final Map<String, Integer> values = new LinkedHashMap<>();

    public void add(String status) {
        values.merge(status, 1, Integer::sum);
    }

    public Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(values);
    }
}
