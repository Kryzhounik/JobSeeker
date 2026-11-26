package com.seeker.guifx;

import com.seeker.api.DriverApi;
import com.seeker.applier.ApplierRunner;
import com.seeker.chatevaluator.GptEvaluation;

import javafx.concurrent.Task;
import javafx.scene.control.Button;

import java.util.function.Consumer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class JobActionsController {
    record Controls(Button apply, Button eval, Button letter) { }

    private final Controls controls;
    private final BackgroundProcessPane.Area backgroundProcesses;
    private final java.util.function.Supplier<JobRecord> selection;
    private final java.util.function.Supplier<DriverApi> api;
    private final Consumer<String> message;
    private boolean detailEnabled;
    private boolean applyRunning, chatEvalRunning, letterRunning;
    private Task<ApplierRunner.Result> applyTask;
    private Task<GptEvaluation.Result> chatEvalTask;
    private ApplyProgressPane applyProgress;
    private GptEvaluationProgressPane chatEvalProgress, letterProgress;
    private final ExecutorService applyExecutor = executor("gui-application-preparation");
    private final ExecutorService chatEvalExecutor = executor("gui-gpt-evaluation");
    private final ExecutorService letterExecutor = executor("gui-cover-letter");

    JobActionsController(Controls controls, BackgroundProcessPane.Area backgroundProcesses,
                         java.util.function.Supplier<JobRecord> selection,
                         java.util.function.Supplier<DriverApi> api, Consumer<String> message) {
        this.controls = controls;
        this.backgroundProcesses = backgroundProcesses;
        this.selection = selection;
        this.api = api;
        this.message = message;
        refreshButtons();
    }

    void setDetailEnabled(boolean enabled) {
        detailEnabled = enabled;
        refreshButtons();
    }

    void refreshButtons() {
        JobRecord job = selection.get();
        boolean unavailable = !detailEnabled || job == null;
        controls.letter().setDisable(unavailable || letterRunning);
        controls.apply().setDisable(unavailable || applyRunning || job.sourceUrl().isBlank() || job.easyApply());
        controls.eval().setDisable(unavailable || chatEvalRunning || job.sourceUrl().isBlank());
    }

    private static ExecutorService executor(String name) {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        });
    }

    void startApply() {
        JobRecord job = selection.get();
        if (applyRunning || job == null || job.sourceUrl().isBlank() || job.easyApply()) return;
        applyRunning = true;
        refreshButtons();
        if (applyProgress == null) applyProgress = new ApplyProgressPane(backgroundProcesses);
        ApplyProgressPane progressPane = applyProgress;
        progressPane.begin(job.sourceUrl());
        Task<ApplierRunner.Result> task = new Task<>() {
            @Override protected ApplierRunner.Result call() throws Exception {
                return api.get().prepareApplication(job.sourceUrl());
            }
        };
        applyTask = task;
        task.setOnSucceeded(event -> {
            applyRunning = false;
            applyTask = null;
            refreshButtons();
            ApplierRunner.Result result = task.getValue();
            progressPane.complete(result);
            message.accept("Apply: " + result.status());
        });
        task.setOnFailed(event -> {
            applyRunning = false;
            applyTask = null;
            refreshButtons();
            progressPane.fail(task.getException());
            message.accept("Application preparation failed");
        });
        applyExecutor.execute(task);
    }

    void startChatEval() {
        JobRecord job = selection.get();
        if (chatEvalRunning || api.get() == null || job == null || job.sourceUrl().isBlank()) return;
        int jobId = job.jobId();
        String url = job.sourceUrl();
        chatEvalRunning = true;
        refreshButtons();
        if (chatEvalProgress == null) chatEvalProgress = new GptEvaluationProgressPane(backgroundProcesses);
        GptEvaluationProgressPane progressPane = chatEvalProgress;
        progressPane.begin(url);
        Task<GptEvaluation.Result> task = new Task<>() {
            @Override protected GptEvaluation.Result call() throws Exception {
                return api.get().evaluateWithChatGPT(url, jobId);
            }
        };
        chatEvalTask = task;
        task.setOnSucceeded(event -> {
            chatEvalRunning = false;
            chatEvalTask = null;
            refreshButtons();
            GptEvaluation.Result result = task.getValue();
            progressPane.complete(result);
        });
        task.setOnFailed(event -> {
            chatEvalRunning = false;
            chatEvalTask = null;
            refreshButtons();
            Throwable error = task.getException();
            progressPane.fail(error);
        });
        chatEvalExecutor.execute(task);
    }

    void startLetter() {
        JobRecord job = selection.get();
        if (letterRunning || job == null || api.get() == null) return;
        letterRunning = true;
        refreshButtons();
        if (letterProgress == null) letterProgress = new GptEvaluationProgressPane(backgroundProcesses, "Letter");
        letterProgress.begin(job.sourceUrl());
        Task<String> task = new Task<>() {
            @Override protected String call() throws Exception { return api.get().coverLetter(job.jobId()); }
        };
        task.setOnSucceeded(event -> {
            letterRunning = false;
            refreshButtons();
            letterProgress.complete(task.getValue());
        });
        task.setOnFailed(event -> {
            letterRunning = false;
            refreshButtons();
            letterProgress.fail(task.getException());
        });
        letterExecutor.execute(task);
    }

    void shutdown() {
        if (applyTask != null) applyTask.cancel(true);
        if (chatEvalTask != null) chatEvalTask.cancel(true);
        applyExecutor.shutdownNow();
        chatEvalExecutor.shutdownNow();
        letterExecutor.shutdownNow();
    }
}

