package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

public record ScopeItem(
        String source,
        @SerializedName("job_id") String jobId,
        String title
) {
}
