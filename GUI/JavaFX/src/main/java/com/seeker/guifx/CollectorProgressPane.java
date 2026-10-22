package com.seeker.guifx;

import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.collector.linkedin.collection.CollectionReport;

import java.util.Map;

class CollectorProgressPane extends BackgroundProcessPane {
    private int limit;
    private int accepted;

    CollectorProgressPane(Area area) {
        this(area, "Collect");
    }

    protected CollectorProgressPane(Area area, String title, String... extraFields) {
        super(area, title, fields(extraFields));
    }

    private static String[] fields(String[] extraFields) {
        String[] collectionFields = {"Run ID", "Stage", "Location", "Search", "Page",
                "Cards on page", "Processed", "Accepted / limit", "Skipped by preview",
                "Rejected by content", "Open failures", "Incomplete data", "Skipped at limit"};
        return java.util.stream.Stream.concat(java.util.Arrays.stream(collectionFields),
                java.util.Arrays.stream(extraFields)).toArray(String[]::new);
    }

    void begin() {
        beginProcess();
        limit = 0;
        accepted = 0;
        setField("Stage", "Starting");
        updateDock();
    }

    void update(CollectionProgress progress) {
        if (!progress.runId().isBlank()) setField("Run ID", progress.runId());
        if (progress.limit() > 0) limit = progress.limit();
        setProcessState(switch (progress.stage()) {
            case "complete" -> State.COMPLETED;
            case "blocked", "login_required" -> State.FAILED;
            default -> State.RUNNING;
        });
        setField("Stage", stageName(progress.stage()));
        setField("Location", progress.location());
        setField("Search", switch (progress.search()) {
            case "account_remote" -> "AccountRemote";
            case "priority" -> "Priority companies";
            case "general" -> "General";
            case "seed" -> "Session seed";
            default -> progress.search();
        });
        setField("Page", progress.page() > 0 ? Integer.toString(progress.page()) : "");
        setField("Cards on page", Integer.toString(progress.cards()));
        showCounts(progress.acceptedCount(), progress.outcomes());
        setDetails(switch (progress.message()) {
            case "limit_reached" -> "Accepted-vacancy limit reached.";
            case "plan_exhausted" -> "Search plan exhausted.";
            default -> progress.message();
        });
    }

    void complete(CollectionReport result) {
        if (!result.runId().isBlank()) setField("Run ID", result.runId());
        setField("Stage", stageName(result.status()));
        showCounts(result.acceptedCount(), result.outcomes());
        if (!result.message().isBlank()) setDetails(result.message());
        setProcessState("complete".equals(result.status()) ? State.COMPLETED : State.FAILED);
        updateDock();
    }

    void fail(Throwable error) {
        setField("Stage", "Failed");
        setDetails(error == null ? "Unknown error" : String.valueOf(error.getMessage()));
        setProcessState(State.FAILED);
        updateDock();
    }

    private void showCounts(int accepted, Map<String, Integer> outcomes) {
        this.accepted = accepted;
        setField("Accepted / limit", accepted + (limit > 0 ? " / " + limit : ""));
        int processed = outcomes.values().stream().mapToInt(Integer::intValue).sum()
                - outcomes.getOrDefault("not_processed_due_to_limit", 0);
        setField("Processed", Integer.toString(processed));
        setCount("Skipped by preview", outcomes, "preview_filtered");
        setCount("Rejected by content", outcomes, "content_filtered");
        setCount("Open failures", outcomes, "open_failed");
        setCount("Incomplete data", outcomes, "incomplete_raw");
        setCount("Skipped at limit", outcomes, "not_processed_due_to_limit");
        updateDock();
    }

    private void updateDock() {
        setSummary(accepted + (limit > 0 ? "/" + limit : ""));
        setContext(fieldText("Stage") + "\n" + fieldText("Run ID"));
    }

    private void setCount(String name, Map<String, Integer> outcomes, String key) {
        setField(name, Integer.toString(outcomes.getOrDefault(key, 0)));
    }

    private static String stageName(String value) {
        return switch (value) {
            case "starting" -> "Starting";
            case "starting_browser" -> "Starting browser";
            case "checking_session" -> "Checking LinkedIn session";
            case "opening_seed" -> "Preparing search session";
            case "opening_search" -> "Opening search";
            case "reading_cards" -> "Reading search results";
            case "filtering_preview" -> "Filtering preview";
            case "opening_job" -> "Loading vacancy";
            case "filtering_content" -> "Saving and filtering content";
            case "processing" -> "Processing vacancies";
            case "complete" -> "Complete";
            case "login_required" -> "LinkedIn login required";
            case "blocked" -> "Stopped with error";
            default -> value;
        };
    }
}
