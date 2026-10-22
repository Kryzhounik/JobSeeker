package com.seeker.orchestrator;

/** Snapshot of the current workflow operation, emitted on the caller's thread. */
public record WorkflowProgress(String runId, String stage, int completedCount, int totalCount,
                               String jobId, int rejectedCount, String message) {
    public WorkflowProgress {
        jobId = jobId == null ? "" : jobId;
        message = message == null ? "" : message;
    }
}
