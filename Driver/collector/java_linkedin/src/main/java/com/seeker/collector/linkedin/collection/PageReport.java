package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

public record PageReport(
        int sequence,
        String label,
        String search,
        int start,
        @SerializedName("requested_url") String requestedUrl,
        @SerializedName("actual_url") String actualUrl,
        String layout,
        @SerializedName("expected_count") int expectedCount,
        @SerializedName("total_results") Integer totalResults,
        @SerializedName("materialized_count") int materializedCount,
        @SerializedName("new_count") int newCount,
        @SerializedName("target_new_count") int targetNewCount,
        @SerializedName("scroll_iterations") int scrollIterations,
        @SerializedName("unchanged_iterations") int unchangedIterations,
        @SerializedName("card_ids_hash") String cardIdsHash,
        boolean terminal,
        @SerializedName("terminal_reason") String terminalReason,
        @SerializedName("next_count") int nextCount,
        @SerializedName("next_visible") boolean nextVisible,
        @SerializedName("next_disabled") boolean nextDisabled,
        @SerializedName("next_aria_disabled") String nextAriaDisabled,
        @SerializedName("next_label") String nextLabel,
        @SerializedName("stop_reason") String stopReason
) {
    PageReport withStopReason(String value) {
        return new PageReport(
                sequence,
                label,
                search,
                start,
                requestedUrl,
                actualUrl,
                layout,
                expectedCount,
                totalResults,
                materializedCount,
                newCount,
                targetNewCount,
                scrollIterations,
                unchangedIterations,
                cardIdsHash,
                terminal,
                terminalReason,
                nextCount,
                nextVisible,
                nextDisabled,
                nextAriaDisabled,
                nextLabel,
                value
        );
    }
}
