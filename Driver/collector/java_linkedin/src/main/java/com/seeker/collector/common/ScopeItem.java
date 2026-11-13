package com.seeker.collector.common;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.JsonAdapter;
import java.util.Map;

public record ScopeItem(
        String source,
        @SerializedName("job_id") String jobId,
        String title,
        @JsonAdapter(value = OptionalFieldsAdapter.class, nullSafe = false) Map<String, Object> fields
) {
    public ScopeItem(String source, String jobId, String title) {
        this(source, jobId, title, null);
    }

    public ScopeItem {
        fields = fields == null ? null : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(fields));
    }
}
