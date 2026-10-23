package com.seeker.guifx;

import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.codex.UsageLimitExceededException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Displays backend-owned progress without interpreting stages or counters. */
final class SnapshotProgressPane extends BackgroundProcessPane {
    private static final DateTimeFormatter RETRY_TIME = DateTimeFormatter
            .ofPattern("dd.MM.uuuu HH:mm:ss z").withZone(ZoneId.systemDefault());
    private String snapshotDetails = "";

    SnapshotProgressPane(Area area, String title) {
        super(area, title, "Run ID", "Stage", "Action");
    }

    void begin() {
        beginProcess();
        replaceExtraFields(Map.of());
        snapshotDetails = "";
    }

    void update(ProgressSnapshot progress) {
        setField("Run ID", progress.runId());
        setField("Stage", progress.stage());
        setField("Action", progress.action());
        replaceExtraFields(progress.fields());
        snapshotDetails = progress.details();
        setDetails(snapshotDetails);
        setSummary(progress.action());
        setContext(progress.stage() + "\n" + progress.action() + "\n" + fieldText("Run ID"));
    }

    void complete(boolean success) {
        finishProcess(success ? State.COMPLETED : State.FAILED);
    }

    void fail(Throwable error) {
        String message = error == null ? null : error.getMessage();
        boolean limited = error instanceof UsageLimitExceededException;
        if (message == null || message.isBlank()) {
            message = limited ? "Codex usage limit reached." : "Unknown error";
        }
        if (error instanceof UsageLimitExceededException limit) {
            message = "Codex usage limit: " + message + (limit.getRetryAt() == null
                    ? "\nRetry time is unknown."
                    : "\nRetry at: " + RETRY_TIME.format(limit.getRetryAt()));
        }
        setDetails(snapshotDetails == null || snapshotDetails.isBlank()
                ? message : snapshotDetails + "\n\n" + message);
        finishProcess(limited ? State.WAITING : State.FAILED);
    }
}
