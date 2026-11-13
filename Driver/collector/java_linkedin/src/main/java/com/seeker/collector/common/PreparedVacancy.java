package com.seeker.collector.common;

import java.util.Map;

/** Source-prepared description and structured fields, ready for common processing. */
public record PreparedVacancy(Preview preview, String description, Boolean easyApply,
                              Map<String, Object> fields) {
    public PreparedVacancy {
        if (preview == null || description == null || description.isBlank()) {
            throw new IllegalArgumentException("Vacancy preview and description are required");
        }
        fields = fields == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }

    public PreparedVacancy withDescription(String text) {
        return new PreparedVacancy(preview, text, easyApply, fields);
    }
}
