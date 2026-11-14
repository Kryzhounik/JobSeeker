package com.seeker.orchestrator;

import com.google.gson.annotations.SerializedName;
import com.seeker.collector.common.ScopeItem;

/** Publication identity, independent of its title or current processing stage. */
record JobKey(String source, @SerializedName("job_id") String jobId) {
    static JobKey of(ScopeItem item) { return new JobKey(item.source(), item.jobId()); }
    @Override public String toString() { return source + ":" + jobId; }
}
