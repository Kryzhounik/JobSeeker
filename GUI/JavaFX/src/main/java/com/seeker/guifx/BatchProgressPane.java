package com.seeker.guifx;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.orchestrator.WorkflowProgress;
import com.seeker.orchestrator.WorkflowResult;

final class BatchProgressPane extends CollectorProgressPane {
    BatchProgressPane(Area area) {
        super(area, "Batch", "Workflow progress", "Current job", "Rejected by agent", "Saved");
    }

    @Override
    void begin() {
        super.begin();
        setField("Rejected by agent", "0");
        setField("Saved", "0");
    }

    @Override
    void update(CollectionProgress progress) {
        super.update(progress);
        if ("complete".equals(progress.stage())) {
            setField("Stage", "Collection complete; preparing analysis");
            setProcessState(State.RUNNING);
            setContext(fieldText("Stage") + "\n" + progress.runId());
        }
    }

    void update(WorkflowProgress progress) {
        setField("Run ID", progress.runId());
        String stage = switch (progress.stage()) {
            case "loading_statuses" -> "Loading processing scope";
            case "relevance_filter" -> "Agent relevance filter";
            case "initializing_analysis" -> "Preparing analysis";
            case "job_facts" -> "Extracting job facts";
            case "candidate_fit" -> "Evaluating candidate fit";
            case "job_interest" -> "Calculating job interest";
            case "saving" -> "Saving results";
            case "complete" -> "Finishing workflow";
            default -> progress.stage();
        };
        setField("Stage", stage);
        setField("Workflow progress", progress.completedCount() + " / " + progress.totalCount());
        setField("Current job", progress.jobId());
        if ("relevance_filter".equals(progress.stage())) {
            setField("Rejected by agent", Integer.toString(progress.rejectedCount()));
        }
        if ("saving".equals(progress.stage())) {
            setField("Saved", Integer.toString(progress.completedCount()));
        }
        setDetails(progress.message());
        setProcessState(State.RUNNING);
        setSummary(progress.completedCount() + "/" + progress.totalCount() + " " + stage);
        setContext(stage + "\n" + progress.runId());
    }

    void complete(WorkflowResult result) {
        if (result.runId() != null) setField("Run ID", result.runId());
        boolean success = "complete".equals(result.status());
        setField("Stage", success ? "Complete" : "Stopped: " + result.status());
        setField("Rejected by agent", Integer.toString(result.agentFilteredCount()));
        setField("Saved", Integer.toString(result.processedCount()));
        setField("Current job", "");
        if (success) setField("Workflow progress", result.processedCount() + " / " + result.processedCount());
        if (!result.message().isBlank()) setDetails(result.message());
        setProcessState(success ? State.COMPLETED : State.FAILED);
        setSummary(success ? "Saved " + result.processedCount() : result.status());
        setContext(fieldText("Stage") + "\n" + fieldText("Run ID"));
    }

    @Override
    void fail(Throwable error) {
        String stage = fieldText("Stage");
        super.fail(error);
        setField("Stage", "Failed: " + stage);
        setSummary("Failed");
        setContext(stage + "\n" + fieldText("Run ID"));
    }
}
