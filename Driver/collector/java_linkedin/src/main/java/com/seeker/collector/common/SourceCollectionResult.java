package com.seeker.collector.common;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/** One source's contribution to the shared collection run. */
public record SourceCollectionResult(String source, String status, int limit,
        @SerializedName("accepted_count") int acceptedCount, Map<String, Integer> outcomes,
        @SerializedName("stop_reason") String stopReason, String message) {
    public SourceCollectionResult { outcomes = Map.copyOf(outcomes); }
}
