package com.seeker.collector.linkedin.collection;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record PreviewDecision(
        @SerializedName("preview_decision") String decision,
        @SerializedName("preview_reason") String reason,
        @SerializedName("preview_rule") String rule,
        @SerializedName("preview_blocked_terms") List<String> blockedTerms
) {
    public boolean shouldOpen() {
        return "open".equalsIgnoreCase(decision);
    }
}
