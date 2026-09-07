package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

public record Preview(
        @SerializedName("job_id") String jobId,
        @SerializedName("source_url") String sourceUrl,
        String title,
        String company,
        String location,
        String workplace,
        String salary,
        String label,
        int start,
        int index
) {
}
