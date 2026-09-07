package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

public record ProcessResult(
        String source,
        @SerializedName("job_id") String jobId,
        String title,
        String status,
        String reason
) {
    public boolean accepted() {
        return "raw_saved".equals(status);
    }
}
