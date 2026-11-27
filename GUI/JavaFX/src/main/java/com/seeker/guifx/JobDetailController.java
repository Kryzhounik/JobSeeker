package com.seeker.guifx;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class JobDetailController {
    @FXML private HBox statusActions;
    @FXML private SplitPane detailSplit;
    @FXML private Hyperlink sourceLink;
    @FXML private Button openButton;
    @FXML private Button applyButton;
    @FXML private Button letterButton;
    @FXML private Button chatEvalButton;
    @FXML private GridPane metadataGrid;
    @FXML private TableView<TechnologyRecord> skillsTable;
    @FXML private TableColumn<TechnologyRecord, String> technologyColumn;
    @FXML private TableColumn<TechnologyRecord, String> requirementColumn;
    @FXML private TableColumn<TechnologyRecord, String> levelColumn;
    @FXML private TableColumn<TechnologyRecord, String> rawColumn;
    @FXML private TextArea summaryArea;
    @FXML private TextArea fullTextArea;
    @FXML private ToggleButton textToggle;
    @FXML private VBox skillsPane;

    @FXML private TextField titleField;
    @FXML private TextField companyField;
    @FXML private TextField idField;
    @FXML private TextField sourceIdField;
    @FXML private TextField scoreField;
    @FXML private TextField fitField;
    @FXML private TextField interestField;
    @FXML private TextField statusField;
    @FXML private TextField roleField;
    @FXML private TextField seniorityField;
    @FXML private TextField locationField;
    @FXML private TextField remoteScopeField;
    @FXML private TextField remoteTypeField;
    @FXML private TextField relocationField;
    @FXML private TextField salaryField;
    @FXML private TextField languagesField;
    @FXML private TextField addedField;

    private Supplier<ClientData> data;
    private Runnable refreshJobs;
    private Consumer<String> message;
    private Consumer<String> openUrl;
    private JobActionsController jobActions;
    private JobRecord selectedJob;
    private int detailLoadedJobId = -1;
    private boolean scoreSaveRunning;
    private Task<JobDetail> pendingDetailTask;
    private final ThreadPoolExecutor detailExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "job-detail-loader");
                thread.setDaemon(true);
                return thread;
            }
    );
    private long detailRequestId;

    @FXML private void initialize() {
        configureSkills();
        textToggle.setOnAction(event -> showText(textToggle.isSelected()));
        sourceLink.setOnAction(event -> openSelectedSource());
        openButton.setOnAction(event -> openSelectedSource());
        fitField.setOnAction(event -> saveScores());
        interestField.setOnAction(event -> saveScores());
        fitField.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) saveScores();
        });
        interestField.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) saveScores();
        });
    }

    void configure(Supplier<ClientData> data, Runnable refreshJobs, Consumer<String> message,
                   Consumer<String> openUrl, JobActionsController jobActions) {
        this.data = data;
        this.refreshJobs = refreshJobs;
        this.message = message;
        this.openUrl = openUrl;
        this.jobActions = jobActions;
        applyButton.setOnAction(event -> jobActions.startApply());
        letterButton.setOnAction(event -> jobActions.startLetter());
        chatEvalButton.setOnAction(event -> jobActions.startChatEval());
        setDetailEnabled(false);
    }

    JobRecord selectedJob() { return selectedJob; }
    HBox statusActions() { return statusActions; }
    SplitPane split() { return detailSplit; }
    JobActionsController.Controls actionControls() {
        return new JobActionsController.Controls(applyButton, chatEvalButton, letterButton);
    }

    void loadForNavigation(int jobId, Consumer<JobDetail> success, Consumer<Throwable> failure) {
        Task<JobDetail> task = new Task<>() {
            @Override protected JobDetail call() throws Exception { return data.get().loadDetail(jobId); }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> failure.accept(task.getException()));
        detailExecutor.execute(task);
    }

    private void configureSkills() {
        FillLastColumnResizePolicy.install(skillsTable);
        bindTechnology(technologyColumn, TechnologyRecord::technology);
        bindTechnology(requirementColumn, TechnologyRecord::requirement);
        bindTechnology(levelColumn, TechnologyRecord::level);
        bindTechnology(rawColumn, TechnologyRecord::raw);
        for (TableColumn<TechnologyRecord, String> column : List.of(
                technologyColumn, requirementColumn, levelColumn
        )) {
            CopyableTextCell.install(column);
        }
        CopyableTextCell.installExpandable(rawColumn);
    }

    private void saveScores() {
        JobRecord job = selectedJob;
        if (job == null
                || job.jobId() != detailLoadedJobId
                || scoreSaveRunning) {
            return;
        }
        String fitText = fitField.getText().strip();
        String interestText = interestField.getText().strip();
        if (fitText.equals(job.fit()) && interestText.equals(job.interest())) {
            return;
        }

        final int fit;
        final int interest;
        try {
            fit = Integer.parseInt(fitText);
            interest = Integer.parseInt(interestText);
            if (fit < 0 || fit > 100) {
                throw new IllegalArgumentException("Fit must be from 0 to 100.");
            }
            if (interest < 0) {
                throw new IllegalArgumentException("Interest must be 0 or greater.");
            }
        } catch (RuntimeException error) {
            fitField.setText(job.fit());
            interestField.setText(job.interest());
            message.accept(error.getMessage() == null
                    ? "Fit and Interest must be whole numbers."
                    : error.getMessage());
            return;
        }

        scoreSaveRunning = true;
        fitField.setDisable(true);
        interestField.setDisable(true);
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() {
                return data.get().saveScores(job.jobId(), fit, interest);
            }
        };
        task.setOnSucceeded(event -> {
            scoreSaveRunning = false;
            int score = task.getValue();
            JobRecord updated = new JobRecord(
                    job.jobId(), Integer.toString(score), Integer.toString(fit),
                    Integer.toString(interest), job.status(), job.remoteScope(),
                    job.relocation(), job.location(), job.companyDisplay(), job.title(),
                    job.role(), job.seniority(), job.language(), job.salary(),
                    job.addedAt(), job.reasonCode(), job.reason(), job.companyId(),
                    job.applicationId(),
                    job.sourceUrl(), job.easyApply(), job.duplicateParent(), job.contextForJobId()
            );
            refreshJobs.run();
            if (selectedJob != null
                    && selectedJob.jobId() == job.jobId()) {
                selectedJob = updated;
                if (job.jobId() == detailLoadedJobId) {
                    scoreField.setText(Integer.toString(score));
                    fitField.setText(Integer.toString(fit));
                    interestField.setText(Integer.toString(interest));
                }
            }
            boolean detailReady = selectedJob != null
                    && selectedJob.jobId() == detailLoadedJobId;
            fitField.setDisable(!detailReady);
            interestField.setDisable(!detailReady);
            message.accept("Scores saved");
        });
        task.setOnFailed(event -> {
            scoreSaveRunning = false;
            if (selectedJob != null
                    && selectedJob.jobId() == job.jobId()
                    && job.jobId() == detailLoadedJobId) {
                fitField.setText(job.fit());
                interestField.setText(job.interest());
            }
            boolean detailReady = selectedJob != null
                    && selectedJob.jobId() == detailLoadedJobId;
            fitField.setDisable(!detailReady);
            interestField.setDisable(!detailReady);
            message.accept("Score update failed: " + task.getException().getMessage());
        });
        detailExecutor.execute(task);
    }

    private static void bindTechnology(
            TableColumn<TechnologyRecord, String> column,
            java.util.function.Function<TechnologyRecord, String> value
    ) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                safe(value.apply(cell.getValue()))
        ));
    }

    void loadDetail(JobRecord job) {
        detailLoadedJobId = -1;
        setDetailEnabled(false);
        selectedJob = job;
        detailRequestId++;
        long requestId = detailRequestId;
        if (pendingDetailTask != null) {
            pendingDetailTask.cancel(true);
            detailExecutor.purge();
        }
        if (job == null) {
            clearDetail();
            return;
        }
        sourceLink.setText(job.sourceUrl());
        sourceLink.setTooltip(new Tooltip(job.sourceUrl()));
        Task<JobDetail> task = new Task<>() {
            @Override
            protected JobDetail call() throws Exception {
                return data.get().loadDetail(job.jobId());
            }
        };
        pendingDetailTask = task;
        task.setOnSucceeded(event -> {
            if (requestId == detailRequestId) {
                pendingDetailTask = null;
                showDetail(task.getValue());
            }
        });
        task.setOnFailed(event -> {
            if (requestId == detailRequestId) {
                pendingDetailTask = null;
                message.accept(
                        "Detail load failed: " + task.getException().getMessage()
                );
            }
        });
        detailExecutor.execute(task);
    }

    private void showDetail(JobDetail detail) {
        JobRecord job = selectedJob;
        if (job == null || job.jobId() != Integer.parseInt(detail.id())) {
            return;
        }
        set(titleField, detail.title());
        set(companyField, detail.company());
        set(idField, detail.id());
        set(sourceIdField, detail.sourceId());
        set(scoreField, detail.score());
        set(fitField, detail.fit());
        set(interestField, detail.interest());
        set(statusField, detail.status());
        set(roleField, detail.role());
        set(seniorityField, detail.seniority());
        set(locationField, detail.location());
        set(remoteScopeField, detail.remoteScope());
        set(remoteTypeField, detail.remoteType());
        set(relocationField, detail.relocation());
        set(salaryField, detail.salary());
        set(languagesField, detail.languages());
        set(addedField, JobsTableView.formatDate(detail.addedAt()));
        skillsTable.setItems(FXCollections.observableArrayList(detail.technologies()));
        summaryArea.setText(detail.summary());
        fullTextArea.setText(detail.readableText());
        detailLoadedJobId = job.jobId();
        setDetailEnabled(true);
        sourceLink.setDisable(job.sourceUrl().isBlank());
        openButton.setDisable(job.sourceUrl().isBlank());
        jobActions.refreshButtons();
        applyButton.setTooltip(job.easyApply() ? new Tooltip("LinkedIn Easy Apply") : null);
        fitField.setDisable(scoreSaveRunning);
        interestField.setDisable(scoreSaveRunning);
        showText(textToggle.isSelected());
    }

    void clearDetail() {
        selectedJob = null;
        detailLoadedJobId = -1;
        sourceLink.setText("");
        for (TextField field : List.of(
                titleField, companyField, idField, sourceIdField, scoreField, fitField,
                interestField, statusField, roleField, seniorityField, locationField,
                remoteScopeField, remoteTypeField, relocationField, salaryField,
                languagesField, addedField
        )) {
            field.clear();
        }
        skillsTable.getItems().clear();
        summaryArea.clear();
        fullTextArea.clear();
        setDetailEnabled(false);
    }

    private void setDetailEnabled(boolean enabled) {
        jobActions.setDetailEnabled(enabled);
        metadataGrid.setDisable(!enabled);
        skillsPane.setDisable(!enabled);
        sourceLink.setDisable(!enabled);
        openButton.setDisable(!enabled);
        textToggle.setDisable(!enabled);
    }

    private void showText(boolean showText) {
        skillsPane.setVisible(!showText);
        skillsPane.setManaged(!showText);
        fullTextArea.setVisible(showText);
        fullTextArea.setManaged(showText);
        textToggle.setText(showText ? "Skills" : "Text");
    }

    private void openSelectedSource() {
        if (selectedJob == null || selectedJob.sourceUrl().isBlank()) {
            return;
        }
        openUrl.accept(selectedJob.sourceUrl());
    }

    private static void set(TextField field, String value) { field.setText(safe(value)); }
    private static String safe(String value) { return value == null ? "" : value; }

    void shutdown() {
        if (pendingDetailTask != null) pendingDetailTask.cancel(true);
        detailExecutor.shutdownNow();
    }
}

