package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;
import com.seeker.collector.linkedin.support.JsonSupport;

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
    public void emit() {
        System.err.println("COLLECTOR_PROGRESS " + JsonSupport.GSON.toJson(this));
    }

    static void starting(String runId, String stage, int limit) {
        new CollectionProgress(runId, stage, "", "", 0, 0, 0, limit, Map.of(), "").emit();
    }
}
