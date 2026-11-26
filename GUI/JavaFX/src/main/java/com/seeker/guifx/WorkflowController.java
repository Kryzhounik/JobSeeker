package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.core.CollectionSettingsLoader;

import com.seeker.api.DriverApi;
import com.seeker.collector.common.CollectionReport;
import com.seeker.orchestrator.WorkflowResult;
import com.seeker.tools.api.ProgressSnapshot;
import com.seeker.codex.UsageLimitExceededException;

import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class WorkflowController {
    record Controls(Button collect, Button batch, Button recommendations, Label status) { }

    private final DriverApi driverApi;
    private final java.nio.file.Path settingsRoot;
    private final BackgroundProcessPane.Area backgroundProcesses;
    private final java.util.function.Supplier<Stage> owner;
    private final Controls controls;
    private final Runnable refreshJobs;
    private final Runnable refreshCollected;
    private final Runnable refreshRuns;
    private final javafx.beans.property.ReadOnlyBooleanWrapper running =
            new javafx.beans.property.ReadOnlyBooleanWrapper();
    private SnapshotProgressPane collectorProgress;
    private SnapshotProgressPane batchProgress;
    private SnapshotProgressPane resumeProgress;
    private SnapshotProgressPane recommendationsProgress;
    private final ExecutorService collectionExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gui-collector");
        thread.setDaemon(true);
        return thread;
    });

    WorkflowController(DriverApi driverApi, java.nio.file.Path settingsRoot,
                       BackgroundProcessPane.Area backgroundProcesses, java.util.function.Supplier<Stage> owner,
                       Controls controls, Runnable refreshJobs, Runnable refreshCollected, Runnable refreshRuns) {
        this.driverApi = driverApi;
        this.settingsRoot = settingsRoot;
        this.backgroundProcesses = backgroundProcesses;
        this.owner = owner;
        this.controls = controls;
        this.refreshJobs = refreshJobs;
        this.refreshCollected = refreshCollected;
        this.refreshRuns = refreshRuns;
        updateCollectionButtons();
    }

    javafx.beans.property.ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }

    void startRecommendations() {
        if (running.get()) return;
        if (recommendationsProgress == null) {
            recommendationsProgress = new SnapshotProgressPane(backgroundProcesses, "Recommendations");
        }
        runWorkflow(recommendationsProgress, null, true);
    }

    void startCollect() {
        confirmCollection("Collect", this::collect);
    }

    private void confirmCollection(String operation, Consumer<CollectionSettings> confirmed) {
        if (running.get() || driverApi == null) return;
        running.set(true);
        updateCollectionButtons();
        Task<CollectionSettings> task = new Task<>() {
            @Override protected CollectionSettings call() throws Exception {
                return CollectionSettingsLoader.loadForEditing(settingsRoot);
            }
        };
        task.setOnSucceeded(event -> {
            running.set(false);
            updateCollectionButtons();
            new CollectionSettingsDialog(owner.get(), operation, task.getValue()).showAndWait().ifPresent(confirmed);
        });
        task.setOnFailed(event -> {
            running.set(false);
            updateCollectionButtons();
            var alert = new Alert(Alert.AlertType.ERROR, task.getException().getMessage());
            alert.initOwner(owner.get()); alert.setTitle("Collection settings load failed"); alert.show();
        });
        collectionExecutor.execute(task);
    }

    private void collect(CollectionSettings settings) {
        if (running.get()) return;
        running.set(true);
        updateCollectionButtons();
        if (collectorProgress == null) {
            collectorProgress = new SnapshotProgressPane(backgroundProcesses, "Collect");
        }
        SnapshotProgressPane progressPane = collectorProgress;
        progressPane.begin();
        Task<CollectionReport> task = new Task<>() {
            @Override protected CollectionReport call() throws Exception {
                return driverApi.collectBatch(settings, progress -> Platform.runLater(() -> progressPane.update(progress)));
            }
        };
        task.setOnSucceeded(event -> {
            running.set(false);
            updateCollectionButtons();
            progressPane.complete("complete".equals(task.getValue().status()));
            refreshJobs.run();
            refreshCollected.run();
        });
        task.setOnFailed(event -> {
            collectionFailed(progressPane, task.getException(), "LinkedIn collector");
            refreshCollected.run();
        });
        collectionExecutor.execute(task);
    }

    private void updateCollectionButtons() {
        controls.collect().setDisable(running.get());
        controls.batch().setDisable(running.get());
        controls.recommendations().setDisable(running.get());
    }

    private void collectionFailed(SnapshotProgressPane progressPane, Throwable error, String title) {
        running.set(false);
        updateCollectionButtons();
        progressPane.fail(error);
        controls.status().setText(title + (error instanceof UsageLimitExceededException
                ? " stopped: Codex usage limit" : " failed"));
    }

    void startBatch() {
        confirmCollection("Batch", settings -> {
            if (batchProgress == null) batchProgress = new SnapshotProgressPane(backgroundProcesses, "Batch");
            runWorkflow(batchProgress, null, false, settings);
        });
    }

    void startResume(CollectionRunDto run) {
        if (running.get() || driverApi == null || run == null) return;
        if (resumeProgress == null) resumeProgress = new SnapshotProgressPane(backgroundProcesses, "Resume");
        runWorkflow(resumeProgress, run.runId());
    }

    private void runWorkflow(SnapshotProgressPane progressPane, String resumeRunId) {
        runWorkflow(progressPane, resumeRunId, false);
    }

    private void runWorkflow(SnapshotProgressPane progressPane, String resumeRunId, boolean recommendations) {
        runWorkflow(progressPane, resumeRunId, recommendations, null);
    }

    private void runWorkflow(SnapshotProgressPane progressPane, String resumeRunId, boolean recommendations,
                             CollectionSettings settings) {
        running.set(true);
        updateCollectionButtons();
        if (resumeRunId == null) progressPane.begin();
        else progressPane.begin(resumeRunId);
        Task<WorkflowResult> task = new Task<>() {
            @Override protected WorkflowResult call() throws Exception {
                Consumer<ProgressSnapshot> progress = update -> Platform.runLater(() -> progressPane.update(update));
                if (recommendations) return driverApi.checkRecommendations(progress);
                return resumeRunId == null ? driverApi.processBatch(settings, progress)
                        : driverApi.resume(resumeRunId, progress);
            }
        };
        task.setOnSucceeded(event -> {
            running.set(false);
            updateCollectionButtons();
            progressPane.complete("complete".equals(task.getValue().status()));
            refreshJobs.run();
            refreshCollected.run();
            refreshRuns.run();
        });
        task.setOnFailed(event -> {
            collectionFailed(progressPane, task.getException(), recommendations ? "Recommendations"
                    : resumeRunId == null ? "Batch" : "Resume");
            refreshJobs.run();
            refreshCollected.run();
            refreshRuns.run();
        });
        collectionExecutor.execute(task);
    }

    void shutdown() { collectionExecutor.shutdownNow(); }
}

