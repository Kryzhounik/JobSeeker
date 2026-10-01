package com.seeker.guifx;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
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
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class MainController {
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

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
    @FXML private Label statusLabel;
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
    private JobRecord selectedJob;
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
        textToggle.setOnAction(event -> showText(textToggle.isSelected()));
        setDetailEnabled(false);

        refreshButton.setDisable(true);
        statusLabel.setText("Starting data connection...");
        Task<ClientData> connectionTask = new Task<>() {
            @Override
            protected ClientData call() throws Exception {
                return new ClientData();
            }
        };
        connectionTask.setOnSucceeded(event -> {
            clientData = connectionTask.getValue();
            refreshJobs();
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
        bind(titleColumn, JobRecord::title);
        bind(roleColumn, JobRecord::role);
        bind(seniorityColumn, JobRecord::seniority);
        bind(languageColumn, JobRecord::language);
        bind(salaryColumn, JobRecord::salary);
        bind(addedColumn, row -> formatDate(row.addedAt()));
        bind(reasonCodeColumn, JobRecord::reasonCode);
        bind(reasonColumn, JobRecord::reason);

        scoreColumn.setComparator(numericComparator());
        fitColumn.setComparator(numericComparator());
        interestColumn.setComparator(numericComparator());
        addedColumn.setComparator(Comparator.comparing(
                MainController::parseDate,
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));

        bindTechnology(technologyColumn, TechnologyRecord::technology);
        bindTechnology(requirementColumn, TechnologyRecord::requirement);
        bindTechnology(levelColumn, TechnologyRecord::level);
        bindTechnology(rawColumn, TechnologyRecord::raw);
        jobsTable.getSelectionModel().setSelectionMode(
                javafx.scene.control.SelectionMode.MULTIPLE
        );
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

    private void refreshJobs() {
        if (clientData == null) {
            return;
        }
        refreshButton.setDisable(true);
        statusLabel.setText("Loading vacancies...");
        Task<List<JobRecord>> task = new Task<>() {
            @Override
            protected List<JobRecord> call() throws Exception {
                List<String> statuses = clientData.loadStatusValues();
                return clientData.loadJobs(statuses, true);
            }
        };
        task.setOnSucceeded(event -> {
            jobsTable.setItems(FXCollections.observableArrayList(task.getValue()));
            refreshButton.setDisable(false);
            statusLabel.setText(task.getValue().size() + " vacancies");
            if (!jobsTable.getItems().isEmpty()) {
                jobsTable.getSelectionModel().selectFirst();
            } else {
                clearDetail();
            }
        });
        task.setOnFailed(event -> {
            refreshButton.setDisable(false);
            statusLabel.setText("Refresh failed: " + task.getException().getMessage());
        });
        Thread worker = new Thread(task, "jobs-load");
        worker.setDaemon(true);
        worker.start();
    }

    private void loadDetail(JobRecord job) {
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
        setDetailEnabled(true);
        sourceLink.setText(job.sourceUrl());
        sourceLink.setTooltip(new Tooltip(job.sourceUrl()));
        Task<JobDetail> task = new Task<>() {
            @Override
            protected JobDetail call() throws Exception {
                return clientData.loadDetail(job.sourceUrl());
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
        if (job == null || !job.sourceUrl().equals(detail.sourceUrl())) {
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
        showText(textToggle.isSelected());
    }

    private void clearDetail() {
        selectedJob = null;
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

    void shutdown() {
        if (pendingDetailTask != null) {
            pendingDetailTask.cancel(true);
        }
        detailExecutor.shutdownNow();
    }

}
