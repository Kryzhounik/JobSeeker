package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.control.SelectionMode;
import javafx.scene.Cursor;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainController {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILTER_DATE = DateTimeFormatter
            .ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    @FXML private TableView<JobRecord> jobsTable;
    @FXML private TableColumn<JobRecord, String> scoreColumn;
    @FXML private TableColumn<JobRecord, String> fitColumn;
    @FXML private TableColumn<JobRecord, String> interestColumn;
    @FXML private TableColumn<JobRecord, String> statusColumn;
    @FXML private TableColumn<JobRecord, String> remoteColumn;
    @FXML private TableColumn<JobRecord, String> relocationColumn;
    @FXML private TableColumn<JobRecord, String> locationColumn;
    @FXML private TableColumn<JobRecord, String> companyColumn;
    @FXML private TableColumn<JobRecord, String> titleColumn;
    @FXML private TableColumn<JobRecord, String> roleColumn;
    @FXML private TableColumn<JobRecord, String> seniorityColumn;
    @FXML private TableColumn<JobRecord, String> languageColumn;
    @FXML private TableColumn<JobRecord, String> salaryColumn;
    @FXML private TableColumn<JobRecord, String> addedColumn;
    @FXML private TableColumn<JobRecord, String> reasonCodeColumn;
    @FXML private TableColumn<JobRecord, String> reasonColumn;

    @FXML private Button refreshButton;
    @FXML private Button collectButton;
    @FXML private Button linkedinCheckButton;
    @FXML private Button refilterButton;
    @FXML private Button refilterDetailButton;
    @FXML private Button blackTitlesButton;
    @FXML private Button searchButton;
    @FXML private Button clearButton;
    @FXML private Button companiesButton;
    @FXML private Button applicationsButton;
    @FXML private Button collectedButton;
    @FXML private Button configButton;
    @FXML private Label statusLabel;
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
    private AuxiliaryWindows auxiliaryWindows;
    private SettingsStore settingsStore;
    private TitleBlacklist titleBlacklist;
    private Stage stage;
    private JobRecord selectedJob;
    private int detailLoadedJobId = -1;
    private final Map<String, CheckBox> statusChecks = new LinkedHashMap<>();
    private final ObjectMapper json = new ObjectMapper();
    private final ThreadPoolExecutor operationExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(), runnable -> {
                Thread thread = new Thread(runnable, "gui-long-operation");
                thread.setDaemon(true);
                return thread;
            }
    );
    private final PauseTransition settingsSaveDelay = new PauseTransition(Duration.millis(250));
    private boolean restoringWindowSettings = true;
    private boolean scoreSaveRunning;
    private boolean operationRunning;
    private List<String> availableStatuses = List.of();
    private Map<String, String> reasonCodeDescriptions = Map.of();
    private String focusAfterRefreshUrl = "";
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
        configureColumns();
        configureSelection();
        sourceLink.setOnAction(event -> openSelectedSource());
        openButton.setOnAction(event -> openSelectedSource());
        refreshButton.setOnAction(event -> refreshJobs());
        collectButton.setOnAction(event -> startCollect());
        linkedinCheckButton.setOnAction(event -> startLinkedInCheck());
        refilterButton.setOnAction(event -> startRefilter(false));
        refilterDetailButton.setOnAction(event -> startRefilter(true));
        blackTitlesButton.setOnAction(event -> runOperation("Opening Black titles...",
                () -> { titleBlacklist.openInNotepad(); return null; },
                ignored -> statusLabel.setText("Opened Black titles"), "Black titles failed"));
        textToggle.setOnAction(event -> showText(textToggle.isSelected()));
        searchButton.setOnAction(event -> refreshJobs());
        clearButton.setOnAction(event -> clearTextFilters());
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
        Task<ClientSession> connectionTask = new Task<>() {
            @Override
            protected ClientSession call() throws Exception {
                ClientData data = new ClientData();
                return new ClientSession(data, data.loadStatusValues(),
                        data.loadReasonCodeDescriptions());
            }
        };
        connectionTask.setOnSucceeded(event -> {
            clientData = connectionTask.getValue().clientData();
            auxiliaryWindows = new AuxiliaryWindows(
                    clientData, stage, this::focusJobFromAuxiliary, settingsStore,
                    this::addTitleToBlacklist
            );
            loadStatusFilters(connectionTask.getValue().statuses());
            availableStatuses = connectionTask.getValue().statuses();
            reasonCodeDescriptions = connectionTask.getValue().reasonCodeDescriptions();
            Label reasonHeader = new Label("Reason code");
            reasonHeader.setTooltip(new Tooltip(reasonCodeDescriptions.entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + entry.getValue())
                    .collect(java.util.stream.Collectors.joining("\n"))));
            reasonCodeColumn.setText("");
            reasonCodeColumn.setGraphic(reasonHeader);
            jobsTable.refresh();
            buildStatusActions();
            restoreJobSort();
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
            CheckBox checkBox = new CheckBox(status);
            checkBox.setSelected(settingsStore.statusEnabled(status));
            checkBox.setOnAction(event -> {
                saveStatusFilters();
                refreshJobs();
            });
            statusChecks.put(status, checkBox);
            statusFilters.getChildren().add(checkBox);
        }
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
        runOperation("Saving status...", () -> clientData.setJobStatusByIds(
                jobIds, status, LocalDate.now().toString()), result -> {
            int updated = result.path("updated_count").asInt();
            int created = result.path("created_applications").asInt();
            refreshJobs();
            if (created > 0 && auxiliaryWindows != null) {
                auxiliaryWindows.refreshApplicationsIfOpen();
                auxiliaryWindows.refreshCompaniesIfOpen();
            }
            statusLabel.setText("Status -> " + status + " (" + updated + ")");
        }, "Status update failed");
    }

    private void setOperationButtonsDisabled(boolean disabled) {
        collectButton.setDisable(disabled || operationRunning);
        linkedinCheckButton.setDisable(disabled || operationRunning);
        refilterButton.setDisable(disabled || operationRunning);
        refilterDetailButton.setDisable(disabled || operationRunning);
        statusActions.setDisable(disabled || operationRunning);
    }

    private <T> void runOperation(
            String initialMessage,
            Callable<T> work,
            Consumer<T> success,
            String errorTitle
    ) {
        runTask(initialMessage, updater -> work.call(), success, errorTitle);
    }

    private <T> void runTask(
            String initialMessage,
            TaskWork<T> work,
            Consumer<T> success,
            String errorTitle
    ) {
        if (operationRunning) return;
        operationRunning = true;
        setOperationButtonsDisabled(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                updateMessage(initialMessage);
                return work.run(this::updateMessage);
            }
        };
        task.messageProperty().addListener((obs, old, message) -> {
            if (message != null && !message.isBlank()) statusLabel.setText(message);
        });
        task.setOnSucceeded(event -> {
            operationRunning = false;
            setOperationButtonsDisabled(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            operationRunning = false;
            setOperationButtonsDisabled(false);
            String message = task.getException() == null ? "Unknown error"
                    : String.valueOf(task.getException().getMessage());
            statusLabel.setText(errorTitle);
            Alert alert = new Alert(Alert.AlertType.ERROR, message);
            alert.setTitle(errorTitle);
            alert.initOwner(stage);
            alert.show();
        });
        operationExecutor.execute(task);
    }

    private void startCollect() {
        CollectorRunner collector = new CollectorRunner(ClientData.findProjectRoot());
        runTask("Starting LinkedIn collector...", collector::run, summary -> {
            statusLabel.setText(summary);
            refreshJobs();
            if (auxiliaryWindows != null) auxiliaryWindows.refreshCollectedIfOpen();
        }, "LinkedIn collector failed");
    }

    private void startLinkedInCheck() {
        String closedStatus = availableStatuses.stream()
                .filter(value -> value.equalsIgnoreCase("closed")).findFirst().orElse("");
        LinkedInAvailabilityChecker checker = new LinkedInAvailabilityChecker(
                clientData, ClientData.findProjectRoot(), closedStatus);
        runTask("Loading LinkedIn candidates...", checker::run, summary -> {
            statusLabel.setText(summary.message());
            refreshJobs();
            if (!summary.error().isBlank()) {
                Alert alert = new Alert(Alert.AlertType.ERROR, summary.error());
                alert.setTitle("LinkedIn check stopped");
                alert.initOwner(stage);
                alert.show();
            }
        }, "LinkedIn check failed");
    }

    private void startRefilter(boolean preview) {
        if (preview) {
            runOperation("Collecting Refilter candidates...", clientData::collectRefilterCandidates,
                    this::showRefilterPreview, "Refilter failed");
        } else {
            runOperation("Running Refilter...", () -> {
                JsonNode candidates = clientData.collectRefilterCandidates();
                return clientData.applyRefilterCandidates(candidates);
            }, count -> {
                refreshJobs();
                if (auxiliaryWindows != null) auxiliaryWindows.refreshCollectedIfOpen();
                statusLabel.setText("Refilter applied " + count + " transitions");
            }, "Refilter failed");
        }
    }

    private void addTitleToBlacklist(String value) {
        if (operationRunning) {
            statusLabel.setText("Finish the current operation first");
            return;
        }
        runOperation("Updating title blacklist...", () -> titleBlacklist.add(value), added ->
                statusLabel.setText(added ? "Added to title blacklist: " + value.strip()
                        : "Already in title blacklist: " + value.strip()),
                "Title blacklist failed");
    }

    private void showRefilterPreview(JsonNode candidates) {
        List<RefilterRow> rows = new ArrayList<>();
        candidates.forEach(candidate -> rows.add(new RefilterRow(candidate.deepCopy())));
        TableView<RefilterRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        CheckBox all = new CheckBox("All");
        all.setSelected(true);
        Button confirm = new Button("Confirm selected");
        confirm.setDisable(rows.isEmpty());
        TableColumn<RefilterRow, Boolean> selected = new TableColumn<>("");
        selected.setSortable(false);
        selected.setCellValueFactory(cell -> cell.getValue().selected);
        selected.setCellFactory(column -> new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(event -> {
                    RefilterRow row = getTableRow().getItem();
                    if (row != null) row.selected.set(check.isSelected());
                });
            }
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : check);
                if (!empty && value != null) check.setSelected(value);
            }
        });
        for (RefilterRow row : rows) {
            row.selected.addListener((obs, old, value) -> {
                all.setSelected(!rows.isEmpty() && rows.stream().allMatch(item -> item.selected.get()));
                confirm.setDisable(rows.stream().noneMatch(item -> item.selected.get()));
            });
        }
        all.setOnAction(event -> rows.forEach(row -> row.selected.set(all.isSelected())));
        TableColumn<RefilterRow, String> sourceId = refilterColumn("Source ID", "source_job_id");
        TableColumn<RefilterRow, String> title = refilterColumn("Title", "title");
        title.setCellFactory(column -> new TableCell<>() {
            private final TextField linkedText = linkedTextField();
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                RefilterRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); return; }
                linkedText.setText(value);
                linkedText.setOnMouseClicked(event -> {
                    if (isLinkClick(linkedText, event.getX(), event.getClickCount())) {
                        openUrl(text(row.candidate, "source_url"));
                    }
                });
                setGraphic(linkedText);
            }
        });
        TableColumn<RefilterRow, String> previous = refilterColumn("Previous", "previous_status");
        TableColumn<RefilterRow, String> action = refilterColumn("Action", "action");
        TableColumn<RefilterRow, String> fit = refilterColumn("Fit", "fit");
        TableColumn<RefilterRow, String> original = refilterColumn("Original", "original");
        TableColumn<RefilterRow, String> matched = refilterColumn("Match", "matched");
        for (TableColumn<RefilterRow, String> column : List.of(
                sourceId, previous, action, fit, original, matched)) {
            CopyableTextCell.install(column);
        }
        table.getColumns().setAll(selected, sourceId, title, previous, action, fit, original, matched);
        Label count = new Label("Refilter candidates: " + rows.size());
        Button cancel = new Button("Cancel");
        Stage dialog = new Stage();
        dialog.initOwner(stage);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Refilter detail");
        confirm.setOnAction(event -> {
            ArrayNode chosen = json.createArrayNode();
            rows.stream().filter(row -> row.selected.get())
                    .forEach(row -> chosen.add(row.candidate.deepCopy()));
            if (chosen.isEmpty()) return;
            dialog.close();
            runOperation("Applying selected Refilter transitions...",
                    () -> clientData.applyRefilterCandidates(chosen), applied -> {
                        refreshJobs();
                        if (auxiliaryWindows != null) auxiliaryWindows.refreshCollectedIfOpen();
                        statusLabel.setText("Refilter applied " + applied + " transitions");
                    }, "Refilter failed");
        });
        cancel.setOnAction(event -> dialog.close());
        HBox footer = new HBox(8, all, count, new javafx.scene.layout.Region(), cancel, confirm);
        HBox.setHgrow(footer.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
        VBox content = new VBox(8, table, footer);
        content.setPadding(new javafx.geometry.Insets(10));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        dialog.setScene(new javafx.scene.Scene(content, 1160, 540));
        dialog.setWidth(settingsStore.windowWidth("refilter_detail", 1160));
        dialog.setHeight(settingsStore.windowHeight("refilter_detail", 540));
        dialog.setOnHiding(event -> {
            try {
                settingsStore.saveWindowSize("refilter_detail", dialog.getWidth(), dialog.getHeight());
            } catch (IOException error) {
                statusLabel.setText("Settings save failed: " + error.getMessage());
            }
        });
        dialog.show();
    }

    private TableColumn<RefilterRow, String> refilterColumn(String title, String key) {
        TableColumn<RefilterRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                text(cell.getValue().candidate, key)));
        return column;
    }

    private static String text(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static final class RefilterRow {
        final JsonNode candidate;
        final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        RefilterRow(JsonNode candidate) { this.candidate = candidate; }
    }

    @FunctionalInterface private interface TaskWork<T> {
        T run(Consumer<String> updateMessage) throws Exception;
    }

    private void openUrl(String value) {
        if (value == null || value.isBlank()) return;
        try {
            Desktop.getDesktop().browse(URI.create(value));
        } catch (Exception error) {
            statusLabel.setText("Could not open link: " + error.getMessage());
        }
    }

    private static TextField linkedTextField() {
        TextField field = new TextField();
        field.setEditable(false);
        field.setFocusTraversable(true);
        field.setCursor(Cursor.HAND);
        field.getStyleClass().add("filter-link-cell");
        field.setMaxWidth(Region.USE_COMPUTED_SIZE);
        return field;
    }

    private static boolean isLinkClick(TextField field, double x, int clickCount) {
        Text measure = new Text(field.getText());
        measure.setFont(field.getFont());
        return clickCount == 1 && field.getSelectedText().isEmpty()
                && x <= measure.getLayoutBounds().getWidth() + 12;
    }

    private void refreshJobs() {
        if (clientData == null) {
            return;
        }
        int selectedId = selectedJob == null ? -1 : selectedJob.jobId();
        Map<String, Object> filters;
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
            filters = Map.of(
                    "statuses", statusChecks.entrySet().stream()
                            .filter(entry -> entry.getValue().isSelected())
                            .map(Map.Entry::getKey)
                            .toList(),
                    "show_zero", showZeroCheck.isSelected(),
                    "id_query", idFilterField.getText().strip(),
                    "added_from", addedFrom,
                    "reasons", reasons
            );
        } catch (RuntimeException error) {
            statusLabel.setText("Date must use DD.MM.YYYY");
            return;
        }

        refreshButton.setDisable(true);
        searchButton.setDisable(true);
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
            jobsTable.setItems(FXCollections.observableArrayList(task.getValue()));
            String requestedFocusUrl = focusAfterRefreshUrl;
            focusAfterRefreshUrl = "";
            refreshButton.setDisable(false);
            searchButton.setDisable(false);
            clearButton.setDisable(false);
            statusFilters.setDisable(false);
            showZeroCheck.setDisable(false);
            idFilterField.setDisable(false);
            dateFilterField.setDisable(false);
            reasonFilterField.setDisable(false);
            statusLabel.setText(task.getValue().size() + " vacancies");
            JobRecord restore = task.getValue().stream()
                    .filter(row -> requestedFocusUrl.isBlank()
                            ? row.jobId() == selectedId
                            : row.sourceUrl().equals(requestedFocusUrl))
                    .findFirst()
                    .orElse(null);
            if (restore != null) {
                jobsTable.getSelectionModel().select(restore);
            } else if (!jobsTable.getItems().isEmpty()) {
                jobsTable.getSelectionModel().selectFirst();
            } else {
                clearDetail();
            }
        });
        task.setOnFailed(event -> {
            refreshButton.setDisable(false);
            searchButton.setDisable(false);
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
            settingsStore.saveStatusFilters(values, showZeroCheck.isSelected());
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
        try {
            settingsStore.saveJobSort(
                    key,
                    column.getSortType() == TableColumn.SortType.DESCENDING
            );
        } catch (IOException error) {
            statusLabel.setText("Settings save failed: " + error.getMessage());
        }
    }

    private TableColumn<JobRecord, String> jobColumn(String key) {
        return switch (key) {
            case "score" -> scoreColumn;
            case "fit" -> fitColumn;
            case "interest" -> interestColumn;
            case "status" -> statusColumn;
            case "remote_scope" -> remoteColumn;
            case "relocation" -> relocationColumn;
            case "location" -> locationColumn;
            case "company" -> companyColumn;
            case "title" -> titleColumn;
            case "role" -> roleColumn;
            case "seniority" -> seniorityColumn;
            case "primary_language" -> languageColumn;
            case "salary" -> salaryColumn;
            case "added_at" -> addedColumn;
            case "candidate_fit_reason_code" -> reasonCodeColumn;
            case "candidate_fit_reason" -> reasonColumn;
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

    private record ClientSession(ClientData clientData, List<String> statuses,
                                 Map<String, String> reasonCodeDescriptions) { }

    private void configureColumns() {
        jobsTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        bind(scoreColumn, JobRecord::score);
        bind(fitColumn, JobRecord::fit);
        bind(interestColumn, JobRecord::interest);
        bind(statusColumn, JobRecord::status);
        bind(remoteColumn, JobRecord::remoteScope);
        bind(relocationColumn, JobRecord::relocation);
        bind(locationColumn, JobRecord::location);
        bind(companyColumn, JobRecord::companyDisplay);
        companyColumn.setCellFactory(column -> new TableCell<>() {
            private final TextField linkedText = linkedTextField();
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                JobRecord row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || value == null || value.isBlank() || row.companyId().isBlank()) {
                    setGraphic(null);
                    return;
                }
                linkedText.setText(value);
                linkedText.setOnMouseClicked(event -> {
                    if (isLinkClick(linkedText, event.getX(), event.getClickCount())) {
                        try { showCompanies(Integer.parseInt(row.companyId())); }
                        catch (NumberFormatException ignored) { }
                    }
                });
                setGraphic(linkedText);
            }
        });
        bind(titleColumn, JobRecord::title);
        TitleTextCell.install(titleColumn, this::addTitleToBlacklist);
        bind(roleColumn, JobRecord::role);
        bind(seniorityColumn, JobRecord::seniority);
        bind(languageColumn, JobRecord::language);
        bind(salaryColumn, JobRecord::salary);
        bind(addedColumn, row -> formatDate(row.addedAt()));
        bind(reasonCodeColumn, JobRecord::reasonCode);
        bind(reasonColumn, JobRecord::reason);

        for (TableColumn<JobRecord, String> column : List.of(
                scoreColumn, fitColumn, interestColumn, statusColumn, remoteColumn,
                relocationColumn, locationColumn, companyColumn,
                roleColumn, seniorityColumn, languageColumn, salaryColumn,
                reasonColumn
        )) {
            CopyableTextCell.install(column);
        }
        FilterLinkCell.install(
                addedColumn,
                row -> formatDate(row.addedAt()),
                this::filterFromDate
        );
        FilterLinkCell.install(reasonCodeColumn, JobRecord::reasonCode, this::filterByReason,
                code -> reasonCodeDescriptions.getOrDefault(code, ""));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                JobRecord row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); return; }
                if ("Applied".equalsIgnoreCase(value) && !row.applicationId().isBlank()) {
                    TextField linkedText = linkedTextField();
                    linkedText.setText(value);
                    linkedText.setOnMouseClicked(event -> {
                        if (isLinkClick(linkedText, event.getX(), event.getClickCount())) {
                            try { showApplications(Integer.parseInt(row.applicationId())); }
                            catch (NumberFormatException ignored) { }
                        }
                    });
                    setGraphic(linkedText);
                } else {
                    TextField copyable = new TextField(value);
                    copyable.setEditable(false);
                    copyable.getStyleClass().add("copyable-cell");
                    copyable.setPadding(javafx.geometry.Insets.EMPTY);
                    copyable.setMaxWidth(Double.MAX_VALUE);
                    setGraphic(copyable);
                }
            }
        });

        scoreColumn.setComparator(numericComparator());
        fitColumn.setComparator(numericComparator());
        interestColumn.setComparator(numericComparator());
        addedColumn.setComparator(Comparator.comparing(
                MainController::parseDisplayDate,
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));

        bindTechnology(technologyColumn, TechnologyRecord::technology);
        bindTechnology(requirementColumn, TechnologyRecord::requirement);
        bindTechnology(levelColumn, TechnologyRecord::level);
        bindTechnology(rawColumn, TechnologyRecord::raw);
        for (TableColumn<TechnologyRecord, String> column : List.of(
                technologyColumn, requirementColumn, levelColumn, rawColumn
        )) {
            CopyableTextCell.install(column);
        }
        jobsTable.getSelectionModel().setSelectionMode(
                javafx.scene.control.SelectionMode.MULTIPLE
        );
        jobsTable.setSortPolicy(table -> {
            boolean sorted = TableView.DEFAULT_SORT_POLICY.call(table);
            saveJobSort();
            return sorted;
        });
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
                    job.sourceUrl()
            );
            for (int index = 0; index < jobsTable.getItems().size(); index++) {
                if (jobsTable.getItems().get(index).jobId() == job.jobId()) {
                    if (score == 0 && !showZeroCheck.isSelected()) {
                        refreshJobs();
                    } else {
                        jobsTable.getItems().set(index, updated);
                    }
                    break;
                }
            }
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

    private static void bind(
            TableColumn<JobRecord, String> column,
            java.util.function.Function<JobRecord, String> value
    ) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                safe(value.apply(cell.getValue()))
        ));
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
        set(addedField, formatDate(detail.addedAt()));
        skillsTable.setItems(FXCollections.observableArrayList(detail.technologies()));
        summaryArea.setText(detail.summary());
        fullTextArea.setText(detail.readableText());
        detailLoadedJobId = job.jobId();
        setDetailEnabled(true);
        sourceLink.setDisable(job.sourceUrl().isBlank());
        openButton.setDisable(job.sourceUrl().isBlank());
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
        try {
            Desktop.getDesktop().browse(URI.create(selectedJob.sourceUrl()));
        } catch (Exception error) {
            statusLabel.setText("Could not open link: " + error.getMessage());
        }
    }

    private void showCompanies(Integer companyId) {
        if (auxiliaryWindows != null) auxiliaryWindows.showCompanies(companyId);
    }

    private void showApplications(Integer applicationId) {
        if (auxiliaryWindows != null) auxiliaryWindows.showApplications(applicationId);
    }

    private void focusJobFromAuxiliary(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) return;
        for (JobRecord job : jobsTable.getItems()) {
            if (job.sourceUrl().equals(sourceUrl)) {
                jobsTable.getSelectionModel().select(job);
                jobsTable.scrollTo(job);
                stage.toFront();
                return;
            }
        }
        idFilterField.setText(sourceUrl);
        focusAfterRefreshUrl = sourceUrl;
        refreshJobs();
        stage.toFront();
    }

    private static void set(TextField field, String value) {
        field.setText(safe(value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static Comparator<String> numericComparator() {
        return Comparator.comparingLong(value -> {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return Long.MIN_VALUE;
            }
        });
    }

    private static String formatDate(String value) {
        LocalDate date = parseDate(value);
        return date == null ? safe(value) : date.format(DISPLAY_DATE);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.length() >= 10 ? value.substring(0, 10) : value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static LocalDate parseDisplayDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, FILTER_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    void shutdown() {
        if (auxiliaryWindows != null) auxiliaryWindows.shutdown();
        settingsSaveDelay.stop();
        operationExecutor.shutdownNow();
        saveWindowSettings();
        if (pendingDetailTask != null) {
            pendingDetailTask.cancel(true);
        }
        detailExecutor.shutdownNow();
    }

}
