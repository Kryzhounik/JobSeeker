package com.seeker.guifx;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RunsResumeTest {
    @BeforeAll static void startJavaFx() { Platform.startup(() -> { }); }
    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void resumeAvailabilityTracksSelectionStatusAndBusyState() throws Exception {
        FxTestSupport.run(() -> {
                TableView<CollectionRunDto> table = new TableView<>();
                SimpleBooleanProperty busy = new SimpleBooleanProperty();
                List<String> calls = new ArrayList<>();
                Button resume = RunsController.resumeButton(table, busy, row -> calls.add(row.runId()));
                check(resume.isDisabled(), "Resume is available without a selection");
                for (String status : List.of("running", "complete", "failed", "blocked")) {
                    CollectionRunDto row = new CollectionRunDto(status + "-run", "", "", status,
                            "Relevance filter", "complete", "", 100, "", "");
                    table.getItems().add(row);
                    table.getSelectionModel().select(row);
                    boolean allowed = !status.equals("running") && !status.equals("complete");
                    check(resume.isDisabled() != allowed, "Incorrect availability for " + status);
                    int before = calls.size();
                    resume.fire();
                    check(calls.size() == before + (allowed ? 1 : 0), "Disabled button dispatched Resume");
                    if (allowed) check(calls.getLast().equals(row.runId()), "Wrong selected Run ID");
                    busy.set(true);
                    check(resume.isDisabled(), "Concurrent workflow did not disable Resume");
                    resume.fire();
                    check(calls.size() == before + (allowed ? 1 : 0), "Duplicate Resume was dispatched");
                    busy.set(false);
                    check(resume.isDisabled() != allowed, "Workflow completion did not restore Resume");
                }
                table.getSelectionModel().clearSelection();
                check(resume.isDisabled(), "Clearing selection did not disable Resume");
                return null;
        });
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
