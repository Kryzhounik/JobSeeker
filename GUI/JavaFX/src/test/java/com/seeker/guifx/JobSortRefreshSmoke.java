package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class JobSortRefreshSmoke {
    public static void main(String[] args) throws Exception {
        Path settings = Files.createTempFile("seeker-sort-", ".json");
        Files.writeString(settings, "{\"job_sort\":{\"column\":\"score\",\"descending\":true}}");
        System.setProperty("seeker.gui.settings.path", settings.toString());
        Platform.startup(() -> { });
        try {
            FXMLLoader loader = new FXMLLoader(JobSortRefreshSmoke.class.getResource(
                    "/com/seeker/guifx/main-view.fxml"));
            onFx(() -> {
                Parent root = loader.load();
                ((MainController) loader.getController()).initializeData(
                        new com.seeker.api.DriverApi(ClientData.findProjectRoot()));
                new Scene(root, 1200, 800);
                return null;
            });
            @SuppressWarnings("unchecked")
            TableView<JobRecord> jobs = (TableView<JobRecord>) loader.getNamespace().get("jobsTable");
            HBox filters = (HBox) loader.getNamespace().get("statusFilters");
            Label status = (Label) loader.getNamespace().get("statusLabel");
            Button refresh = (Button) loader.getNamespace().get("refreshButton");
            waitUntil(() -> status.getText().endsWith(" vacancies") && !jobs.getItems().isEmpty(),
                    "Initial vacancies did not load");
            onFx(() -> { assertSorted(jobs); return null; });
            onFx(() -> { refresh.fire(); return null; });
            waitUntil(() -> status.getText().endsWith(" vacancies") && !refresh.isDisabled(),
                    "Refresh did not finish");
            onFx(() -> { assertSorted(jobs); return null; });
            CheckBox checked = (CheckBox) filters.getChildren().stream()
                    .filter(node -> node instanceof CheckBox box && box.getText().equals("Checked"))
                    .findFirst().orElseThrow();
            onFx(() -> { checked.fire(); return null; });
            waitUntil(() -> status.getText().endsWith(" vacancies") && !refresh.isDisabled(),
                    "Status filter did not finish");
            onFx(() -> { assertSorted(jobs); return null; });
            System.out.println("Job sort refresh OK");
        } finally {
            Platform.exit();
            Files.deleteIfExists(settings);
        }
    }

    private static void assertSorted(TableView<JobRecord> jobs) {
        if (jobs.getSortOrder().isEmpty()) throw new AssertionError("Sort order is empty");
        TableColumn<JobRecord, ?> column = jobs.getSortOrder().getFirst();
        if (column != jobs.getColumns().getFirst()) {
            throw new AssertionError("Score is no longer the sort column");
        }
        if (column.getSortType() != TableColumn.SortType.DESCENDING) {
            throw new AssertionError("Sort direction changed");
        }
        int previous = Integer.MAX_VALUE;
        for (JobRecord row : jobs.getItems()) {
            int score = Integer.parseInt(row.score());
            if (score > previous) {
                throw new AssertionError("Scores out of order: " + previous + ", " + score);
            }
            previous = score;
        }
    }

    private static void waitUntil(BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            if (onFx(condition::getAsBoolean)) return;
            Thread.sleep(100);
        }
        throw new AssertionError(message);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(30, TimeUnit.SECONDS);
    }
}
