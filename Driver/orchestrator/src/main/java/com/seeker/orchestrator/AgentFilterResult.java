package com.seeker.orchestrator;

import com.google.gson.annotations.SerializedName;

import java.util.List;

record AgentFilterResult(
        @SerializedName("nonrelevant_job_ids") List<String> nonrelevantJobIds
) {
}
