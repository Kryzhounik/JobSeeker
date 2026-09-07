package com.seeker.collector.linkedin.collection;

import java.util.LinkedHashMap;
import java.util.Map;

final class OutcomeCounter {
    private final Map<String, Integer> values = new LinkedHashMap<>();

    void add(String status) {
        values.merge(status, 1, Integer::sum);
    }

    Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(values);
    }
}
