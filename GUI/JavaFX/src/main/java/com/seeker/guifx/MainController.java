package com.seeker.guifx;

import com.seeker.api.DriverApi;

import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.io.IOException;
import java.util.Map;
import java.util.List;
import java.util.Set;

public final class MainController {
    @FXML private TableView<JobRecord> jobsTable;
    private JobsTableView jobTable;

    @FXML private Button collectButton;
    @FXML private Button batchButton;
    @FXML private Button recommendationsButton;
    @FXML private Button linkedinCheckButton;
    @FXML private Button refilterButton;
    @FXML private Button refilterDetailButton;
    @FXML private Button blackTitlesButton;
    @FXML private Button runsButton;
    @FXML private Button companiesButton;
    @FXML private Button applicationsButton;
    @FXML private Button collectedButton;
    @FXML private Button configButton;
    @FXML private Label statusLabel;
    @FXML private HBox backgroundProcessDocks;
    @FXML private StackPane backgroundProcessLayer;
    @FXML private SplitPane mainSplit;
    @FXML private JobDetailController jobDetailController;
    @FXML private JobFiltersController jobFiltersController;

    private ClientData clientData;
    private DriverApi driverApi;
    private AuxiliaryWindows auxiliaryWindows;
    private SettingsStore settingsStore;
    private TitleBlacklist titleBlacklist;
    private Stage stage;
    private Set<Integer> duplicateParentIds = Set.of();
    private final PauseTransition settingsSaveDelay = new PauseTransition(Duration.millis(250));
    private boolean restoringWindowSettings = true;
    private BackgroundProcessPane.Area backgroundProcesses;
    private BackgroundTasks tasks;
    private WorkflowController workflows;
    private JobActionsController jobActions;
    private VacancyMaintenanceController maintenance;
    private List<String> availableStatuses = List.of();
    private Map<String, String> reasonCodeDescriptions = Map.of();
    private int focusAfterRefreshId = -1;
    private long refreshRequestId;
    private boolean jobSortRestored;
    private long navigationRequestId;

    @FXML
    private void initialize() {
        settingsStore = new SettingsStore(ClientData.findProjectRoot());
        titleBlacklist = new TitleBlacklist(ClientData.findProjectRoot());
        backgroundProcesses = new BackgroundProcessPane.Area(backgroundProcessLayer, backgroundProcessDocks);
        tasks = new BackgroundTasks(statusLabel::setText, () -> stage);
        jobActions = new JobActionsController(jobDetailController.actionControls(),
                backgroundProcesses, jobDetailController::selectedJob, () -> driverApi, statusLabel::setText);
        jobDetailController.configure(() -> clientData, this::refreshJobs, statusLabel::setText,
                this::openUrl, jobActions);
        jobFiltersController.configure(settingsStore, this::refreshJobs, statusLabel::setText);
        tasks.runningProperty().addListener((obs, old, busy) -> setOperationButtonsDisabled(busy));
        jobTable = new JobsTableView(jobsTable,
                new JobsTableView.Actions(this::showCompanies, this::showApplications,
                        jobFiltersController::filterFromDate, jobFiltersController::filterByReason,
                        this::addTitleToBlacklist),
                row -> duplicateParentIds.contains(row.jobId()), () -> reasonCodeDescriptions, this::saveJobSort);
        jobsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> jobDetailController.loadDetail(current));
        collectButton.setOnAction(event -> { if (workflows != null) workflows.startCollect(); });
        batchButton.setOnAction(event -> { if (workflows != null) workflows.startBatch(); });
        recommendationsButton.setOnAction(event -> {
            if (workflows != null) workflows.startRecommendations();
        });
        linkedinCheckButton.setOnAction(event -> maintenance.startLinkedInCheck());
        refilterButton.setOnAction(event -> maintenance.startRefilter(false));
        refilterDetailButton.setOnAction(event -> maintenance.startRefilter(true));
        blackTitlesButton.setOnAction(event -> maintenance.openBlacklist());
        runsButton.setOnAction(event -> {
            if (auxiliaryWindows != null) auxiliaryWindows.showRuns();
        });
        companiesButton.setOnAction(event -> showCompanies(null));
        applicationsButton.setOnAction(event -> showApplications(null));
        collectedButton.setOnAction(event -> auxiliaryWindows.showCollected());
        configButton.setOnAction(event -> auxiliaryWindows.showConfig());
        maintenance = new VacancyMaintenanceController(() -> clientData, tasks, titleBlacklist,
                settingsStore, () -> stage, this::refreshJobs,
                () -> { if (auxiliaryWindows != null) auxiliaryWindows.refreshCollectedIfOpen(); },
                statusLabel::setText, this::openUrl);
        settingsSaveDelay.setOnFinished(event -> saveWindowSettings());
        mainSplit.getDividers().getFirst().positionProperty().addListener(
                (observable, previous, current) -> scheduleWindowSettingsSave());
        jobDetailController.split().getDividers().getFirst().positionProperty().addListener(
                (observable, previous, current) -> scheduleWindowSettingsSave());
        setOperationButtonsDisabled(true);
        statusLabel.setText("Starting data connection...");
    }

    void initializeData(DriverApi api) {
        initializeData(api, null);
    }

    void initializeData(DriverApi api, java.nio.file.Path databasePath) {
        initializeData(api, databasePath, ClientData.findProjectRoot());
    }

    void initializeData(DriverApi api, java.nio.file.Path databasePath,
                        java.nio.file.Path collectionSettingsRoot) {
        Task<ClientSession> connectionTask = new Task<>() {
            @Override
            protected ClientSession call() throws Exception {
                ClientData data = new ClientData(databasePath);
                return new ClientSession(data, api, data.loadStatusValues(), data.loadReasonCodeDescriptions());
            }
        };
        connectionTask.setOnSucceeded(event -> {
            clientData = connectionTask.getValue().clientData();
            driverApi = connectionTask.getValue().driverApi();
            auxiliaryWindows = new AuxiliaryWindows(
                    clientData, this::focusJobFromAuxiliary, settingsStore,
                    this::addTitleToBlacklist, this::startResume, collectionSettingsRoot
            );
            workflows = new WorkflowController(driverApi, collectionSettingsRoot, backgroundProcesses, () -> stage,
                    new WorkflowController.Controls(collectButton, batchButton, recommendationsButton, statusLabel),
                    this::refreshJobs, auxiliaryWindows::refreshCollectedIfOpen, auxiliaryWindows::refreshRunsIfOpen);
            workflows.runningProperty().addListener((obs, old, busy) -> auxiliaryWindows.setWorkflowRunning(busy));
            jobFiltersController.loadStatuses(connectionTask.getValue().statuses());
            availableStatuses = connectionTask.getValue().statuses();
            reasonCodeDescriptions = connectionTask.getValue().reasonCodeDescriptions();
            Label reasonHeader = new Label("Reason code");
            reasonHeader.setTooltip(new Tooltip(reasonCodeDescriptions.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("\n"))));
            jobTable.column("reasonCode").setText("");
            jobTable.column("reasonCode").setGraphic(reasonHeader);
            jobsTable.refresh();
            buildStatusActions();
            refreshJobs();
            setOperationButtonsDisabled(false);
        });
        connectionTask.setOnFailed(event -> {
            statusLabel.setText("Data connection failed");
            statusLabel.setTooltip(new Tooltip(
                    connectionTask.getException().getMessage()
            ));
        });
        Thread worker = new Thread(connectionTask, "python-data-connection");
        worker.setDaemon(true);
        worker.start();
    }

    void attachStage(Stage stage) {
        this.stage = stage;
        stage.setWidth(settingsStore.windowWidth());
        stage.setHeight(settingsStore.windowHeight());
        stage.widthProperty().addListener((observable, previous, current) ->
                scheduleWindowSettingsSave()
        );
        stage.heightProperty().addListener((observable, previous, current) ->
                scheduleWindowSettingsSave()
        );
        Platform.runLater(() -> {
            mainSplit.setDividerPositions(settingsStore.mainDivider());
            jobDetailController.split().setDividerPositions(settingsStore.detailDivider());
            restoringWindowSettings = false;
        });
    }

    private void buildStatusActions() {
        jobDetailController.statusActions().getChildren().clear();
        jobDetailController.statusActions().getChildren().add(new Label("Set status"));
        for (String status : availableStatuses) {
            Button button = new Button(status);
            button.setOnAction(event -> applyStatusToSelection(status));
            jobDetailController.statusActions().getChildren().add(button);
        }
    }

    private void applyStatusToSelection(String status) {
        List<Integer> jobIds = jobsTable.getSelectionModel().getSelectedItems().stream()
                .map(JobRecord::jobId).distinct().toList();
        if (jobIds.isEmpty()) return;
        tasks.run("Saving status...", () -> clientData.setJobStatusByIds(
                jobIds, status, LocalDate.now().toString()), result -> {
            int updated = result.updatedCount();
            int created = result.createdApplications();
            refreshJobs();
            if (created > 0 && auxiliaryWindows != null) {
                auxiliaryWindows.refreshApplicationsIfOpen();
                auxiliaryWindows.refreshCompaniesIfOpen();
            }
            statusLabel.setText("Status -> " + status + " (" + updated + ")");
        }, "Status update failed");
    }

    private void setOperationButtonsDisabled(boolean disabled) {
        runsButton.setDisable(clientData == null);
        if (workflows == null) {
            collectButton.setDisable(true);
            batchButton.setDisable(true);
            recommendationsButton.setDisable(true);
        }
        linkedinCheckButton.setDisable(disabled || tasks.isRunning());
        refilterButton.setDisable(disabled || tasks.isRunning());
        refilterDetailButton.setDisable(disabled || tasks.isRunning());
        jobDetailController.statusActions().setDisable(disabled || tasks.isRunning());
        jobActions.refreshButtons();
    }

    private void openUrl(String value) {
        if (value == null || value.isBlank()) return;
        try {
            WindowSupport.browse(value);
        } catch (Exception error) {
            statusLabel.setText("Could not open link: " + error.getMessage());
        }
    }

    private void refreshJobs() {
        if (clientData == null) {
            return;
        }
        JobRecord selectedJob = jobDetailController.selectedJob();
        int selectedId = selectedJob == null ? -1 : selectedJob.jobId();
        JobFilters filters;
        try {
            filters = jobFiltersController.snapshot();
        } catch (RuntimeException error) {
            statusLabel.setText("Date must use DD.MM.YYYY");
            return;
        }

        int requestedFocusId = focusAfterRefreshId;
        focusAfterRefreshId = -1;
        long requestId = ++refreshRequestId;

        jobFiltersController.setLoading(true);
        statusLabel.setText("Loading vacancies...");
        Task<List<JobRecord>> task = new Task<>() {
            @Override
            protected List<JobRecord> call() throws Exception {
                return clientData.loadJobs(filters);
            }
        };
        task.setOnSucceeded(event -> {
            if (requestId != refreshRequestId) return;
            List<TableColumn<JobRecord, ?>> sortOrder = List.copyOf(jobsTable.getSortOrder());
            duplicateParentIds = JobRows.parentIds(task.getValue());
            jobsTable.setItems(FXCollections.observableArrayList(JobRows.sorted(task.getValue(), null)));
            if (!jobSortRestored) {
                if (jobsTable.getSortOrder().isEmpty()) restoreJobSort();
                jobSortRestored = true;
            } else if (!sortOrder.isEmpty()) {
                jobsTable.getSortOrder().setAll(sortOrder);
            }
            jobsTable.sort();
            jobFiltersController.setLoading(false);
            statusLabel.setText(task.getValue().size() + " vacancies");
            JobRecord restore = jobsTable.getItems().stream()
                    .filter(row -> row.jobId() == (requestedFocusId < 0
                            ? selectedId : requestedFocusId))
                    .findFirst()
                    .orElse(null);
            if (restore != null) {
                jobsTable.getSelectionModel().select(restore);
                if (requestedFocusId >= 0) jobsTable.scrollTo(restore);
            } else if (requestedFocusId >= 0) {
                jobDetailController.clearDetail();
                statusLabel.setText("Vacancy " + requestedFocusId + " not found in the table");
            } else if (!jobsTable.getItems().isEmpty()) {
                jobsTable.getItems().stream().filter(row -> row.contextForJobId() == null)
                        .findFirst().ifPresent(row -> jobsTable.getSelectionModel().select(row));
            } else {
                jobDetailController.clearDetail();
            }
        });
        task.setOnFailed(event -> {
            if (requestId != refreshRequestId) return;
            jobFiltersController.setLoading(false);
            statusLabel.setText("Refresh failed: " + task.getException().getMessage());
        });
        Thread worker = new Thread(task, "jobs-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void restoreJobSort() {
        TableColumn<JobRecord, String> column = jobColumn(settingsStore.sortColumn());
        if (column == null) {
            return;
        }
        column.setSortType(settingsStore.sortDescending()
                ? TableColumn.SortType.DESCENDING
                : TableColumn.SortType.ASCENDING);
        jobsTable.getSortOrder().setAll(column);
        jobsTable.sort();
    }

    private void saveJobSort() {
        if (settingsStore == null || jobsTable.getSortOrder().isEmpty()) {
            return;
        }
        TableColumn<JobRecord, ?> column = jobsTable.getSortOrder().getFirst();
        String key = jobColumnKey(column);
        if (key.isEmpty()) {
            return;
        }
        boolean descending = column.getSortType() == TableColumn.SortType.DESCENDING;
        if (key.equals(settingsStore.sortColumn())
                && descending == settingsStore.sortDescending()) {
            return;
        }
        try {
            settingsStore.saveJobSort(key, descending);
        } catch (IOException error) {
            statusLabel.setText("Settings save failed: " + error.getMessage());
        }
    }

    private TableColumn<JobRecord, String> jobColumn(String key) {
        return switch (key) {
            case "score" -> jobTable.column("score");
            case "fit" -> jobTable.column("fit");
            case "interest" -> jobTable.column("interest");
            case "status" -> jobTable.column("status");
            case "remote_scope" -> jobTable.column("remote");
            case "relocation" -> jobTable.column("relocation");
            case "location" -> jobTable.column("location");
            case "company" -> jobTable.column("company");
            case "title" -> jobTable.column("title");
            case "role" -> jobTable.column("role");
            case "seniority" -> jobTable.column("seniority");
            case "primary_language" -> jobTable.column("language");
            case "salary" -> jobTable.column("salary");
            case "added_at" -> jobTable.column("added");
            case "candidate_fit_reason_code" -> jobTable.column("reasonCode");
            case "candidate_fit_reason" -> jobTable.column("reason");
            default -> null;
        };
    }

    private String jobColumnKey(TableColumn<JobRecord, ?> column) {
        for (String key : List.of(
                "score", "fit", "interest", "status", "remote_scope", "relocation",
                "location", "company", "title", "role", "seniority", "primary_language",
                "salary", "added_at", "candidate_fit_reason_code", "candidate_fit_reason"
        )) {
            if (jobColumn(key) == column) {
                return key;
            }
        }
        return "";
    }

    private void scheduleWindowSettingsSave() {
        if (restoringWindowSettings || stage == null) {
            return;
        }
        settingsSaveDelay.playFromStart();
    }

    private void saveWindowSettings() {
        if (restoringWindowSettings || stage == null) {
            return;
        }
        try {
            settingsStore.saveWindowSize(stage.getWidth(), stage.getHeight());
            settingsStore.saveDividers(
                    mainSplit.getDividerPositions()[0],
                    jobDetailController.split().getDividerPositions()[0]
            );
        } catch (IOException error) {
            statusLabel.setText("Settings save failed: " + error.getMessage());
        }
    }

    private record ClientSession(ClientData clientData, DriverApi driverApi, List<String> statuses,
                                 Map<String, String> reasonCodeDescriptions) { }

    private void showCompanies(Integer companyId) {
        if (auxiliaryWindows != null) auxiliaryWindows.showCompanies(companyId);
    }

    private void showApplications(Integer applicationId) {
        if (auxiliaryWindows != null) auxiliaryWindows.showApplications(applicationId);
    }

    void focusJobFromAuxiliary(Integer jobId) {
        if (jobId == null) return;
        long requestId = ++navigationRequestId;
        stage.setIconified(false);
        stage.toFront();
        for (JobRecord job : jobsTable.getItems()) {
            if (job.jobId() == jobId) {
                jobsTable.getSelectionModel().select(job);
                jobsTable.scrollTo(job);
                return;
            }
        }
        statusLabel.setText("Locating vacancy " + jobId + "...");
        jobDetailController.loadForNavigation(jobId, detail -> {
            if (requestId != navigationRequestId) return;
            if (!jobFiltersController.prepareForJob(detail)) return;
            focusAfterRefreshId = jobId;
            refreshJobs();
        }, error -> {
            if (requestId == navigationRequestId) {
                statusLabel.setText("Could not locate vacancy: " + error.getMessage());
            }
        });
    }

    void shutdown() {
        if (auxiliaryWindows != null) auxiliaryWindows.shutdown();
        if (workflows != null) workflows.shutdown();
        tasks.shutdown();
        jobActions.shutdown();
        settingsSaveDelay.stop();
        saveWindowSettings();
        jobDetailController.shutdown();
    }

    private void startResume(CollectionRunDto run) {
        if (workflows != null) workflows.startResume(run);
    }

    private void addTitleToBlacklist(String value) { maintenance.addTitleToBlacklist(value); }
}
