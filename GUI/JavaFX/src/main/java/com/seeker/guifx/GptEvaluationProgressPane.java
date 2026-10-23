package com.seeker.guifx;

import com.seeker.chatevaluator.GptEvaluation;

final class GptEvaluationProgressPane extends BackgroundProcessPane {
    GptEvaluationProgressPane(Area area) {
        super(area, "GPT Eval", "Job ID", "URL", "Stage", "Evaluated at");
    }

    void begin(int jobId, String url) {
        beginProcess();
        setField("Job ID", Integer.toString(jobId));
        setField("URL", url);
        setField("Stage", "Evaluating");
        setSummary("Evaluating job " + jobId);
        setContext(url);
    }

    void complete(GptEvaluation.Result result) {
        setField("Stage", "Saved");
        setField("Evaluated at", result.evaluatedAt());
        setDetails("Full answer saved for job " + result.jobId());
        setSummary("Saved job " + result.jobId());
        finishProcess(State.COMPLETED);
    }

    void fail(Throwable error) {
        String stage = error instanceof GptEvaluation.SaveException
                ? "Saving failed" : "Evaluation failed";
        setField("Stage", stage);
        String message = error == null ? null : error.getMessage();
        setDetails(message == null || message.isBlank() ? "Unknown error" : message);
        setSummary(stage);
        finishProcess(State.FAILED);
    }
}
