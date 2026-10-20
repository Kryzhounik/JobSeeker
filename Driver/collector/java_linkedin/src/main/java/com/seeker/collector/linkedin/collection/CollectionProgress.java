package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

public record CollectionProgress(
        @SerializedName("run_id") String runId,
        String stage,
        String location,
        String search,
        int page,
        int cards,
        @SerializedName("accepted_count") int acceptedCount,
        int limit,
        Map<String, Integer> outcomes,
        String message
) {
    public CollectionProgress {
        location = location == null ? "" : location;
        search = search == null ? "" : search;
        message = message == null ? "" : message;
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
    }

    static CollectionProgress starting(String runId, String stage, int limit) {
        return new CollectionProgress(runId, stage, "", "", 0, 0, 0, limit, Map.of(), "");
    }
}
