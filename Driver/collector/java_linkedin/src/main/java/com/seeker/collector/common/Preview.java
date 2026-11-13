package com.seeker.collector.common;

import com.google.gson.annotations.SerializedName;

public record Preview(
        String source,
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
    /** Legacy LinkedIn constructor. Other sources must use the explicit source component. */
    public Preview(String jobId, String sourceUrl, String title, String company, String location,
                   String workplace, String salary, String label, int start, int index) {
        this("linkedin", jobId, sourceUrl, title, company, location, workplace, salary, label, start, index);
    }
}
