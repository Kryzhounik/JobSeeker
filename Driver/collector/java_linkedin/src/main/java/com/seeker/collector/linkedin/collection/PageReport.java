package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

public record PageReport(
        String label,
        String search,
        int start,
        @SerializedName("page_url") String pageUrl,
        @SerializedName("expected_count") int expectedCount,
        @SerializedName("materialized_count") int materializedCount,
        @SerializedName("new_count") int newCount,
        boolean terminal
) {
}
