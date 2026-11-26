package com.seeker.guifx;

import com.seeker.api.DriverApi;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainController {
    private static final DateTimeFormatter FILTER_DATE = DateTimeFormatter
            .ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    @FXML private TableView<JobRecord> jobsTable;
    private JobsTableView jobTable;

    @FXML private Button refreshButton;
    @FXML private Button collectButton;
    @FXML private Button batchButton;
    @FXML private Button recommendationsButton;
    @FXML private Button linkedinCheckButton;
    @FXML private Button refilterButton;
    @FXML private Button refilterDetailButton;
    @FXML private Button blackTitlesButton;
    @FXML private Button clearButton;
    @FXML private Button runsButton;
    @FXML private Button companiesButton;
    @FXML private Button applicationsButton;
    @FXML private Button collectedButton;
    @FXML private Button configButton;
    @FXML private Label statusLabel;
    @FXML private HBox backgroundProcessDocks;
    @FXML private StackPane backgroundProcessLayer;
    @FXML private HBox statusFilters;
    @FXML private HBox statusActions;
    @FXML private CheckBox showZeroCheck;
    @FXML private TextField idFilterField;
    @FXML private TextField dateFilterField;
    @FXML private TextField reasonFilterField;
    @FXML private SplitPane mainSplit;
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

    private ClientData clientData;
    private DriverApi driverApi;
    private AuxiliaryWindows auxiliaryWindows;
    private SettingsStore settingsStore;
    private TitleBlacklist titleBlacklist;
    private Stage stage;
    private JobRecord selectedJob;
    private Set<Integer> duplicateParentIds = Set.of();
    private int detailLoadedJobId = -1;
    private final Map<String, CheckBox> statusChecks = new LinkedHashMap<>();
    private final CheckBox unavailableCheck = new CheckBox("Unavailable");
    private final PauseTransition settingsSaveDelay = new PauseTransition(Duration.millis(250));
    private boolean restoringWindowSettings = true;
    private boolean scoreSaveRunning;
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
    private Task<JobDetail> pendingDetailTask;
    private final ThreadPoolExecutor detailExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "job-detail-loader");
                thread.setDaemon(true);
                return thread;
            }
    );
    private long detailRequestId;

    @FXML
    private void initialize() {
        backgroundProcesses = new BackgroundProcessPane.Area(backgroundProcessLayer, backgroundProcessDocks);
        tasks = new BackgroundTasks(statusLabel::setText, () -> stage);
        jobActions = new JobActionsController(
                new JobActionsController.Controls(applyButton, chatEvalButton, letterButton),
                backgroundProcesses, () -> selectedJob, () -> driverApi, statusLabel::setText);
        tasks.runningProperty().addListener((obs, old, busy) -> setOperationButtonsDisabled(busy));
        jobTable = new JobsTableView(jobsTable,
                new JobsTableView.Actions(this::showCompanies, this::showApplications,
                        this::filterFromDate, this::filterByReason, this::addTitleToBlacklist),
                row -> duplicateParentIds.contains(row.jobId()), () -> reasonCodeDescriptions, this::saveJobSort);
        configureSkills();
        configureSelection();
        sourceLink.setOnAction(event -> openSelectedSource());
        openButton.setOnAction(event -> openSelectedSource());
        applyButton.setOnAction(event -> jobActions.startApply());
        letterButton.setOnAction(event -> jobActions.startLetter());
        chatEvalButton.setOnAction(event -> jobActions.startChatEval());
        refreshButton.setOnAction(event -> refreshJobs());
        collectButton.setOnAction(event -> { if (workflows != null) workflows.startCollect(); });
        batchButton.setOnAction(event -> { if (workflows != null) workflows.startBatch(); });
        recommendationsButton.setOnAction(event -> {
            if (workflows != null) workflows.startRecommendations();
        });
        linkedinCheckButton.setOnAction(event -> maintenance.startLinkedInCheck());
        refilterButton.setOnAction(event -> maintenance.startRefilter(false));
        refilterDetailButton.setOnAction(event -> maintenance.startRefilter(true));
        blackTitlesButton.setOnAction(event -> maintenance.openBlacklist());
        textToggle.setOnAction(event -> showText(textToggle.isSelected()));
        clearButton.setOnAction(event -> clearTextFilters());
        runsButton.setOnAction(event -> {
            if (auxiliaryWindows != null) auxiliaryWindows.showRuns();
        });
        companiesButton.setOnAction(event -> showCompanies(null));
        applicationsButton.setOnAction(event -> showApplications(null));
        collectedButton.setOnAction(event -> auxiliaryWindows.showCollected());
        configButton.setOnAction(event -> auxiliaryWindows.showConfig());
        idFilterField.setOnAction(event -> refreshJobs());
        dateFilterField.setOnAction(event -> refreshJobs());
        reasonFilterField.setOnAction(event -> refreshJobs());
        fitField.setOnAction(event -> saveScores());
        interestField.setOnAction(event -> saveScores());
        fitField.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) {
                saveScores();
            }
        });
        interestField.focusedProperty().addListener((observable, previous, focused) -> {
            if (!focused) {
                saveScores();
            }
        });
        setDetailEnabled(false);
        settingsStore = new SettingsStore(ClientData.findProjectRoot());
        titleBlacklist = new TitleBlacklist(ClientData.findProjectRoot());
        maintenance = new VacancyMaintenanceController(() -> clientData, tasks, titleBlacklist,
                settingsStore, () -> stage, this::refreshJobs,
                () -> { if (auxiliaryWindows != null) auxiliaryWindows.refreshCollectedIfOpen(); },
                statusLabel::setText, this::openUrl);
        settingsSaveDelay.setOnFinished(event -> saveWindowSettings());
        mainSplit.getDividers().getFirst().positionProperty().addListener(
                (observable, previous, current) -> scheduleWindowSettingsSave()
        );
        detailSplit.getDividers().getFirst().positionProperty().addListener(
                (observable, previous, current) -> scheduleWindowSettingsSave()
        );

        refreshButton.setDisable(true);
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
            loadStatusFilters(connectionTask.getValue().statuses());
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
            detailSplit.setDividerPositions(settingsStore.detailDivider());
            restoringWindowSettings = false;
        });
    }

    private void loadStatusFilters(List<String> statuses) {
        statusFilters.getChildren().clear();
        statusChecks.clear();
        for (String status : statuses) {
            if (status.equalsIgnoreCase("Closed")) continue;
            CheckBox checkBox = new CheckBox(status);
            checkBox.setSelected(settingsStore.statusEnabled(status));
            checkBox.setOnAction(event -> {
                saveStatusFilters();
                refreshJobs();
            });
            statusChecks.put(status, checkBox);
            statusFilters.getChildren().add(checkBox);
        }
        unavailableCheck.setSelected(settingsStore.showUnavailable());
        unavailableCheck.setOnAction(event -> {
            saveStatusFilters();
            refreshJobs();
        });
        statusFilters.getChildren().add(unavailableCheck);
        showZeroCheck.setSelected(settingsStore.showZero());
        showZeroCheck.setOnAction(event -> {
            saveStatusFilters();
            refreshJobs();
        });
    }

    private void buildStatusActions() {
        statusActions.getChildren().clear();
        statusActions.getChildren().add(new Label("Set status"));
        for (String status : availableStatuses) {
            Button button = new Button(status);
            button.setOnAction(event -> applyStatusToSelection(status));
            statusActions.getChildren().add(button);
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
        statusActions.setDisable(disabled || tasks.isRunning());
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
        int selectedId = selectedJob == null ? -1 : selectedJob.jobId();
        JobFilters filters;
        try {
            String date = dateFilterField.getText().strip();
            String addedFrom = date.isEmpty()
                    ? ""
                    : LocalDate.parse(date, FILTER_DATE).toString();
            List<String> reasons = java.util.Arrays.stream(
                            reasonFilterField.getText().split(",")
                    )
                    .map(String::strip)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .toList();
            filters = new JobFilters(
                    statusChecks.entrySet().stream()
                            .filter(entry -> entry.getValue().isSelected())
                            .map(Map.Entry::getKey)
                            .toList(),
                    showZeroCheck.isSelected(),
                    unavailableCheck.isSelected(),
                    idFilterField.getText().strip(),
                    addedFrom,
                    reasons
            );
        } catch (RuntimeException error) {
            statusLabel.setText("Date must use DD.MM.YYYY");
            return;
        }

        int requestedFocusId = focusAfterRefreshId;
        focusAfterRefreshId = -1;
        long requestId = ++refreshRequestId;

        refreshButton.setDisable(true);
        clearButton.setDisable(true);
        statusFilters.setDisable(true);
        showZeroCheck.setDisable(true);
        idFilterField.setDisable(true);
        dateFilterField.setDisable(true);
        reasonFilterField.setDisable(true);
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
            refreshButton.setDisable(false);
            clearButton.setDisable(false);
            statusFilters.setDisable(false);
            showZeroCheck.setDisable(false);
            idFilterField.setDisable(false);
            dateFilterField.setDisable(false);
            reasonFilterField.setDisable(false);
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
                clearDetail();
                statusLabel.setText("Vacancy " + requestedFocusId + " not found in the table");
            } else if (!jobsTable.getItems().isEmpty()) {
                jobsTable.getItems().stream().filter(row -> row.contextForJobId() == null)
                        .findFirst().ifPresent(row -> jobsTable.getSelectionModel().select(row));
            } else {
                clearDetail();
            }
        });
        task.setOnFailed(event -> {
            if (requestId != refreshRequestId) return;
            refreshButton.setDisable(false);
            clearButton.setDisable(false);
            statusFilters.setDisable(false);
            showZeroCheck.setDisable(false);
            idFilterField.setDisable(false);
            dateFilterField.setDisable(false);
            reasonFilterField.setDisable(false);
            statusLabel.setText("Refresh failed: " + task.getException().getMessage());
        });
        Thread worker = new Thread(task, "jobs-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void clearTextFilters() {
        idFilterField.clear();
        dateFilterField.clear();
        reasonFilterField.clear();
        refreshJobs();
    }

    private void filterFromDate(String date) {
        dateFilterField.setText(date);
        refreshJobs();
    }

    private void filterByReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        List<String> selected = java.util.Arrays.stream(reasonFilterField.getText().split(","))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .toList();
        if (selected.stream().noneMatch(value -> value.equalsIgnoreCase(reason))) {
            selected = new java.util.ArrayList<>(selected);
            selected.add(reason);
            reasonFilterField.setText(String.join(", ", selected));
        }
        refreshJobs();
    }

    private void saveStatusFilters() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        statusChecks.forEach((status, checkBox) ->
                values.put(status, checkBox.isSelected())
        );
        try {
            settingsStore.saveStatusFilters(values, showZeroCheck.isSelected(),
                    unavailableCheck.isSelected());
        } catch (IOException error) {
            statusLabel.setText("Settings save failed: " + error.getMessage());
        }
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
                    detailSplit.getDividerPositions()[0]
            );
        } catch (IOException error) {
            statusLabel.setText("Settings save failed: " + error.getMessage());
        }
    }

    private record ClientSession(ClientData clientData, DriverApi driverApi, List<String> statuses,
                                 Map<String, String> reasonCodeDescriptions) { }

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
            statusLabel.setText(error.getMessage() == null
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
                return clientData.saveScores(job.jobId(), fit, interest);
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
            refreshJobs();
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
            statusLabel.setText("Scores saved");
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
            statusLabel.setText("Score update failed: " + task.getException().getMessage());
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

    private void configureSelection() {
        jobsTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previous, current) -> loadDetail(current)
        );
    }

    private void loadDetail(JobRecord job) {
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
                return clientData.loadDetail(job.jobId());
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
                statusLabel.setText(
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

    private void clearDetail() {
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
        openUrl(selectedJob.sourceUrl());
    }

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
        Task<JobDetail> task = new Task<>() {
            @Override
            protected JobDetail call() throws Exception {
                return clientData.loadDetail(jobId);
            }
        };
        task.setOnSucceeded(event -> {
            if (requestId != navigationRequestId) return;
            JobDetail detail = task.getValue();
            CheckBox statusCheck = statusChecks.get(detail.status());
            if (statusCheck == null) {
                statusLabel.setText("Unknown vacancy status: " + detail.status());
                return;
            }
            boolean changed = false;
            if (!statusCheck.isSelected()) {
                statusCheck.setSelected(true);
                changed = true;
            }
            if ("0".equals(detail.score()) && !showZeroCheck.isSelected()) {
                showZeroCheck.setSelected(true);
                changed = true;
            }
            if (changed) saveStatusFilters();
            dateFilterField.clear();
            reasonFilterField.clear();
            idFilterField.setText(Integer.toString(jobId));
            focusAfterRefreshId = jobId;
            refreshJobs();
        });
        task.setOnFailed(event -> {
            if (requestId == navigationRequestId) {
                statusLabel.setText("Could not locate vacancy: " + task.getException().getMessage());
            }
        });
        detailExecutor.execute(task);
    }

    private static void set(TextField field, String value) {
        field.setText(safe(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    void shutdown() {
        if (auxiliaryWindows != null) auxiliaryWindows.shutdown();
        if (workflows != null) workflows.shutdown();
        tasks.shutdown();
        jobActions.shutdown();
        settingsSaveDelay.stop();
        saveWindowSettings();
        if (pendingDetailTask != null) pendingDetailTask.cancel(true);
        detailExecutor.shutdownNow();
    }

    private void startResume(CollectionRunDto run) {
        if (workflows != null) workflows.startResume(run);
    }

    private void addTitleToBlacklist(String value) { maintenance.addTitleToBlacklist(value); }
}
