package com.seeker.collector.common;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.JsonAdapter;
import java.util.Map;

public record ProcessResult(
        String source,
        @SerializedName("job_id") String jobId,
        String title,
        String status,
        String reason,
        @JsonAdapter(value = OptionalFieldsAdapter.class, nullSafe = false) Map<String, Object> fields
) {
    public ProcessResult(String source, String jobId, String title, String status, String reason) {
        this(source, jobId, title, status, reason, null);
    }

    public ProcessResult {
        fields = fields == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }
    public boolean accepted() {
        return "raw_saved".equals(status);
    }
}
