package com.seeker.guifx;

import com.seeker.applier.ApplierRunner;

final class ApplyProgressPane extends BackgroundProcessPane {
    ApplyProgressPane(Area area) {
        super(area, "Apply", "Job ID", "URL", "Stage", "Result");
    }

    void begin(int jobId, String url) {
        beginProcess();
        setField("Job ID", Integer.toString(jobId));
        setField("URL", url);
        setField("Stage", "Preparing application");
        setSummary("Preparing job " + jobId);
        setContext(url);
    }

    void complete(ApplierRunner.Result result) {
        setField("Stage", "Finished");
        setField("Result", result.status());
        setDetails((result.submissionObserved() ? "Application may already have been submitted.\n" : "")
                + result.note());
        setSummary(result.status());
        finishProcess(State.COMPLETED);
    }

    void fail(Throwable error) {
        setField("Stage", "Preparation failed");
        String message = error == null ? null : error.getMessage();
        setDetails(message == null || message.isBlank() ? "Unknown error" : message);
        setSummary("Preparation failed");
        finishProcess(State.FAILED);
    }
}
