package com.seeker.guifx;

import com.fasterxml.jackson.databind.JsonNode;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class AuxiliaryWindows {
    private final ClientData data;
    private final Window owner;
    private final Consumer<String> openJob;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "javafx-auxiliary-data");
        thread.setDaemon(true);
        return thread;
    });
    private Stage companiesStage;
    private Stage applicationsStage;
    private Stage collectedStage;
    private TableView<CompanyRow> companiesTable;
    private List<CompanyRow> companyRows = new ArrayList<>();

    AuxiliaryWindows(
            ClientData data,
            Window owner,
            Consumer<String> openJob
    ) {
        this.data = data;
        this.owner = owner;
        this.openJob = openJob;
    }

    void shutdown() {
        ioExecutor.shutdownNow();
    }

    void refreshCompaniesIfOpen() {
        if (companiesStage != null && companiesStage.isShowing()) refreshCompanies(null);
    }

    void refreshApplicationsIfOpen() {
        if (applicationsStage != null && applicationsStage.isShowing()) showApplications(null);
    }

    void refreshCollectedIfOpen() {
        if (collectedStage != null && collectedStage.isShowing()) showCollected();
    }

    private <T> void submit(
            Callable<T> work,
            Consumer<T> success,
            Consumer<Exception> failure
    ) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> success.accept(task.getValue()));
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            failure.accept(error instanceof Exception exception
                    ? exception : new RuntimeException(error));
        });
        ioExecutor.execute(task);
    }

    void showCompanies(Integer focusId) {
        try {
            if (companiesStage == null) {
                companiesStage = stage("Companies", 760, 620);
                companiesTable = new TableView<>();
                companiesTable.setEditable(true);
                companiesTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
                TableColumn<CompanyRow, Boolean> priority = new TableColumn<>("Priority");
                priority.setCellValueFactory(cell -> cell.getValue().priorityProperty());
                priority.setCellFactory(column -> flagCell(true));
                priority.setEditable(true);
                priority.setPrefWidth(75);
                priority.setOnEditCommit(event -> saveCompanyFlag(
                        event.getRowValue(), "set_company_priority", event.getNewValue(), true
                ));
                companiesTable.getColumns().add(priority);
                companiesTable.getColumns().add(stringColumn("Company", "name"));
                TableColumn<CompanyRow, String> linkedin = new TableColumn<>("LinkedIn ID");
                linkedin.setCellValueFactory(cell -> cell.getValue().linkedinIdProperty());
                linkedin.setCellFactory(TextFieldTableCell.forTableColumn());
                linkedin.setOnEditCommit(event -> {
                    CompanyRow row = event.getRowValue();
                    String value = event.getNewValue().strip();
                    submit(() -> {
                        data.setCompanyValue("set_company_linkedin_id", row.id, value);
                        return null;
                    }, ignored -> {
                        row.linkedinId.set(value);
                        row.savedLinkedinId = value;
                    }, error -> {
                        row.linkedinId.set(row.savedLinkedinId);
                        companiesTable.refresh();
                        showError("LinkedIn ID update failed", error);
                    });
                });
                linkedin.setPrefWidth(125);
                companiesTable.getColumns().add(linkedin);
                companiesTable.getColumns().add(numberColumn("Applications", "applications"));
                TableColumn<CompanyRow, Boolean> blacklisted = new TableColumn<>("Blacklisted");
                blacklisted.setCellValueFactory(cell -> cell.getValue().blacklistedProperty());
                blacklisted.setCellFactory(column -> flagCell(false));
                blacklisted.setEditable(true);
                blacklisted.setPrefWidth(95);
                blacklisted.setOnEditCommit(event -> saveCompanyFlag(
                        event.getRowValue(), "set_company_blacklisted", event.getNewValue(), false
                ));
                companiesTable.getColumns().add(blacklisted);

                TextField linkedinField = new TextField();
                linkedinField.setPromptText("LinkedIn ID (optional)");
                TextField nameField = new TextField();
                nameField.setPromptText("Company name");
                Button add = new Button("Add");
                add.setOnAction(event -> {
                    String name = nameField.getText().strip();
                    if (name.isEmpty()) {
                        nameField.requestFocus();
                        return;
                    }
                    String linkedinId = linkedinField.getText().strip();
                    add.setDisable(true);
                    submit(() -> data.createCompany(name, linkedinId), id -> {
                        add.setDisable(false);
                        nameField.clear();
                        linkedinField.clear();
                        refreshCompanies(id);
                    }, error -> {
                        add.setDisable(false);
                        showError("Company could not be added", error);
                    });
                });
                HBox addRow = new HBox(8, new Label("LinkedIn ID"), linkedinField,
                        new Label("Name"), nameField, add);
                companiesStage.setScene(new Scene(new VBox(8, companiesTable, addRow), 760, 620));
            }
            companiesStage.show();
            companiesStage.toFront();
            refreshCompanies(focusId);
        } catch (Exception error) {
            showError("Companies load failed", error);
        }
    }

    private void refreshCompanies(Integer focusId) {
        submit(data::loadCompanies, rows -> {
            companyRows = new ArrayList<>();
            for (JsonNode row : rows) {
                companyRows.add(new CompanyRow(
                        integer(row, "id"), text(row, "name"), text(row, "linkedin_id"),
                        integer(row, "application_count"), integer(row, "priority") != 0,
                        integer(row, "blacklisted") != 0
                ));
            }
            companiesTable.setItems(FXCollections.observableArrayList(companyRows));
            if (focusId != null) {
                companyRows.stream().filter(row -> row.id == focusId).findFirst().ifPresent(row -> {
                    companiesTable.getSelectionModel().select(row);
                    companiesTable.scrollTo(row);
                });
            }
        }, error -> showError("Companies load failed", error));
    }

    private void saveCompanyFlag(CompanyRow row, String method, Boolean value, boolean priority) {
        boolean previous = (priority ? row.priority : row.blacklisted).get();
        submit(() -> {
            data.setCompanyValue(method, row.id, value);
            return null;
        }, ignored -> {
            (priority ? row.priority : row.blacklisted).set(value);
        }, error -> {
            (priority ? row.priority : row.blacklisted).set(previous);
            companiesTable.refresh();
            showError("Company update failed", error);
        });
    }

    private TableCell<CompanyRow, Boolean> flagCell(boolean isPriority) {
        return new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(event -> {
                    CompanyRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) saveCompanyFlag(row,
                            isPriority ? "set_company_priority" : "set_company_blacklisted",
                            check.isSelected(), isPriority);
                });
            }
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : check);
                if (!empty && value != null) check.setSelected(value);
            }
        };
    }

    void showApplications(Integer focusId) {
        submit(() -> {
            List<String> statuses = data.loadApplicationStatuses();
            List<ApplicationRow> rows = new ArrayList<>();
            for (JsonNode row : data.loadApplications()) {
                rows.add(new ApplicationRow(integer(row, "id"), text(row, "source_url"),
                        text(row, "title"), text(row, "company"),
                        nullableInteger(row, "company_id"), text(row, "applied_at"),
                        text(row, "status")));
            }
            return new ApplicationsData(statuses, rows);
        }, loaded -> buildApplications(loaded, focusId),
                error -> showError("Applications load failed", error));
    }

    private void buildApplications(ApplicationsData loaded, Integer focusId) {
            List<String> statuses = loaded.statuses;
            List<ApplicationRow> rows = loaded.rows;
            TableView<ApplicationRow> table = new TableView<>(FXCollections.observableArrayList(rows));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            TableColumn<ApplicationRow, String> title = new TableColumn<>("Title");
            title.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title));
            title.setCellFactory(column -> new HyperlinkCell<>(row -> row.title, row ->
                    openJob.accept(row.sourceUrl)));
            TableColumn<ApplicationRow, String> company = new TableColumn<>("Company");
            company.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().company));
            company.setCellFactory(column -> new HyperlinkCell<>(row -> row.company, row -> {
                if (row.companyId != null) showCompanies(row.companyId);
            }));
            TableColumn<ApplicationRow, String> date = new TableColumn<>("Date");
            date.setCellValueFactory(cell -> new SimpleStringProperty(displayDate(cell.getValue().date)));
            TableColumn<ApplicationRow, String> status = new TableColumn<>("Status");
            status.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().status));
            status.setCellFactory(column -> new TableCell<>() {
                private final ComboBox<String> box = new ComboBox<>(FXCollections.observableArrayList(statuses));
                {
                    box.setMaxWidth(Double.MAX_VALUE);
                    box.setOnAction(event -> {
                        ApplicationRow row = getTableRow().getItem();
                        if (row == null || row.status.equals(box.getValue())) return;
                        String previous = row.status;
                        String nextStatus = box.getValue();
                        box.setDisable(true);
                        submit(() -> {
                            data.setApplicationStatus(row.id, nextStatus);
                            return null;
                        }, ignored -> {
                            row.status = box.getValue();
                            box.setDisable(false);
                            table.refresh();
                        }, error -> {
                            row.status = previous;
                            box.setValue(previous);
                            box.setDisable(false);
                            showError("Application update failed", error);
                        });
                    });
                }
                @Override protected void updateItem(String value, boolean empty) {
                    super.updateItem(value, empty);
                    setGraphic(empty ? null : box);
                    if (!empty) box.setValue(value);
                }
            });
            table.getColumns().setAll(title, company, date, status);
            if (focusId != null) rows.stream().filter(row -> row.id == focusId).findFirst().ifPresent(row -> {
                table.getSelectionModel().select(row);
                table.scrollTo(row);
            });
            if (applicationsStage == null) applicationsStage = stage("Applications", 1050, 650);
            applicationsStage.setScene(new Scene(table, 1050, 650));
            applicationsStage.show();
            applicationsStage.toFront();
    }

    void showCollected() {
        submit(() -> {
            List<CollectedRow> rows = new ArrayList<>();
            for (JsonNode row : data.loadCollectedJobs()) {
                rows.add(new CollectedRow(text(row, "source_job_id"), text(row, "processing_status"),
                        text(row, "collection_method"), text(row, "title"), text(row, "company"),
                        text(row, "collected_location"), text(row, "collected_workplace"),
                        text(row, "collected_salary"), text(row, "source_url"),
                        text(row, "readable_text")));
            }
            return rows;
        }, this::buildCollected, error -> showError("Collected jobs load failed", error));
    }

    private void buildCollected(List<CollectedRow> rows) {
            TableView<CollectedRow> table = new TableView<>(FXCollections.observableArrayList(rows));
            table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
            table.getColumns().setAll(
                    collectedColumn("Source ID", row -> row.sourceId),
                    collectedColumn("Stage", row -> row.stage),
                    collectedColumn("Collector", row -> row.collector),
                    collectedColumn("Title", row -> row.title),
                    collectedColumn("Company", row -> row.company),
                    collectedColumn("Location", row -> row.location),
                    collectedColumn("Workplace", row -> row.workplace),
                    collectedColumn("Salary", row -> row.salary),
                    collectedColumn("URL", row -> row.url)
            );
            ComboBox<String> stageFilter = new ComboBox<>();
            List<String> stages = rows.stream().map(row -> row.stage).distinct().sorted().toList();
            stageFilter.getItems().add("All");
            stageFilter.getItems().addAll(stages);
            stageFilter.setValue("All");
            stageFilter.setOnAction(event -> table.setItems(FXCollections.observableArrayList(
                    rows.stream().filter(row -> stageFilter.getValue().equals("All")
                            || row.stage.equals(stageFilter.getValue())).toList()
            )));
            TextArea text = new TextArea();
            text.setEditable(false);
            text.setWrapText(true);
            table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) ->
                    text.setText(row == null ? "" : row.text));
            table.setRowFactory(view -> {
                var row = new javafx.scene.control.TableRow<CollectedRow>();
                row.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 2 && !row.isEmpty()) openUrl(row.getItem().url);
                });
                return row;
            });
            Button refresh = new Button("Refresh");
            refresh.setOnAction(event -> showCollected());
            Button open = new Button("Open");
            open.setOnAction(event -> {
                CollectedRow row = table.getSelectionModel().getSelectedItem();
                if (row != null) openUrl(row.url);
            });
            if (collectedStage == null) collectedStage = stage("Collected jobs", 1300, 760);
            collectedStage.setScene(new Scene(new BorderPane(table, new HBox(8, refresh, open,
                    new Label("Stage"), stageFilter), null, text, null), 1300, 760));
            collectedStage.show();
            collectedStage.toFront();
    }

    void showConfig() {
        submit(() -> new ConfigData(data.loadConfigRows(), readCollectorLimit()),
                this::buildConfig, error -> showError("Config load failed", error));
    }

    private void buildConfig(ConfigData config) {
            VBox content = new VBox(8);
            for (JsonNode row : config.rows) {
                int key = integer(row, "key");
                CheckBox check = new CheckBox(text(row, "config_name"));
                check.setSelected("1".equals(text(row, "value")));
                check.setOnAction(event -> {
                    boolean enabled = check.isSelected();
                    check.setDisable(true);
                    submit(() -> {
                        data.setConfigValue(key, enabled);
                        return null;
                    }, ignored -> check.setDisable(false), error -> {
                        check.setSelected(!enabled);
                        check.setDisable(false);
                        showError("Config update failed", error);
                    });
                });
                content.getChildren().add(check);
            }
            TextField limit = new TextField(config.limit);
            limit.setPromptText("Positive integer");
            limit.setMaxWidth(100);
            Label limitStatus = new Label();
            Runnable saveLimit = () -> {
                String value = limit.getText().strip();
                try {
                    int parsed = Integer.parseInt(value);
                    if (parsed <= 0) throw new NumberFormatException();
                    String normalized = Integer.toString(parsed);
                    submit(() -> {
                        writeCollectorLimit(normalized);
                        return normalized;
                    }, saved -> {
                        limit.setText(saved);
                        limitStatus.setText("Saved");
                        limit.setDisable(false);
                    }, error -> {
                        limit.setText(config.limit);
                        limitStatus.setText(error.getMessage());
                        limit.setDisable(false);
                    });
                    limit.setDisable(true);
                } catch (RuntimeException error) {
                    limit.setText(config.limit);
                    limitStatus.setText("Enter a positive integer");
                }
            };
            limit.setOnAction(event -> saveLimit.run());
            limit.focusedProperty().addListener((obs, old, focused) -> {
                if (!focused) saveLimit.run();
            });
            content.getChildren().addAll(new Label("LinkedIn collection limit"), limit, limitStatus);
            Stage stage = stage("Config", 420, 400);
            stage.setScene(new Scene(new javafx.scene.control.ScrollPane(content), 420, 400));
            stage.show();
    }

    private <T> TableColumn<T, String> stringColumn(String title, String property) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> {
            Object row = cell.getValue();
            if (row instanceof CompanyRow company) return "name".equals(property)
                    ? new SimpleStringProperty(company.name) : company.linkedinId;
            return new SimpleStringProperty("");
        });
        return column;
    }

    private TableColumn<CompanyRow, Number> numberColumn(String title, String property) {
        TableColumn<CompanyRow, Number> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new javafx.beans.property.SimpleIntegerProperty(
                cell.getValue().applications));
        return column;
    }

    private TableColumn<CollectedRow, String> collectedColumn(
            String title, java.util.function.Function<CollectedRow, String> value
    ) {
        TableColumn<CollectedRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        return column;
    }

    private Stage stage(String title, double width, double height) {
        Stage stage = new Stage();
        stage.initOwner(owner);
        stage.initModality(Modality.NONE);
        stage.setTitle(title);
        stage.setWidth(width);
        stage.setHeight(height);
        return stage;
    }

    private static Path collectorConfigPath() {
        return ClientData.findProjectRoot().resolve("Driver/collector/config/linkedin.properties");
    }

    private static String readCollectorLimit() {
        try {
            for (String line : Files.readAllLines(collectorConfigPath(), StandardCharsets.UTF_8)) {
                String trimmed = line.strip();
                if (trimmed.startsWith("limit=")) return trimmed.substring("limit=".length()).strip();
            }
        } catch (Exception ignored) { }
        return "50";
    }

    private static void writeCollectorLimit(String value) throws Exception {
        Path path = collectorConfigPath();
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        boolean found = false;
        for (int index = 0; index < lines.size(); index++) {
            if (lines.get(index).strip().startsWith("limit=")) {
                lines.set(index, "limit=" + value);
                found = true;
                break;
            }
        }
        if (!found) lines.add("limit=" + value);
        Files.write(path, lines, StandardCharsets.UTF_8);
    }

    private static String displayDate(String value) {
        try { return java.time.LocalDate.parse(value.substring(0, 10)).format(
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
        catch (RuntimeException ignored) { return value; }
    }

    private static String text(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static int integer(JsonNode row, String key) {
        return row.path(key).asInt(0);
    }

    private static Integer nullableInteger(JsonNode row, String key) {
        JsonNode value = row.get(key);
        return value == null || value.isNull() ? null : value.asInt();
    }

    private static void openUrl(String value) {
        try { if (Desktop.isDesktopSupported() && !value.isBlank()) Desktop.getDesktop().browse(URI.create(value)); }
        catch (Exception error) { showError("Could not open link", error); }
    }

    private static void showError(String title, Exception error) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR, error.getMessage());
        alert.setTitle(title);
        alert.show();
    }

    private static class HyperlinkCell<T> extends TableCell<T, String> {
        private final java.util.function.Function<T, String> text;
        private final Consumer<T> action;
        private final TextField linkText = new TextField();
        HyperlinkCell(java.util.function.Function<T, String> text, Consumer<T> action) {
            this.text = text; this.action = action;
            linkText.setEditable(false);
            linkText.setFocusTraversable(true);
            linkText.setCursor(Cursor.HAND);
            linkText.getStyleClass().add("filter-link-cell");
            linkText.setMaxWidth(Region.USE_COMPUTED_SIZE);
            linkText.prefWidthProperty().bind(widthProperty().subtract(8));
            linkText.setOnMouseClicked(event -> {
                Text measure = new Text(linkText.getText());
                measure.setFont(linkText.getFont());
                if (event.getClickCount() == 1 && linkText.getSelectedText().isEmpty()
                        && event.getX() <= measure.getLayoutBounds().getWidth() + 12) {
                    T row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) action.accept(row);
                }
            });
        }
        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            T row = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (row == null) { setGraphic(null); return; }
            linkText.setText(text.apply(row));
            setGraphic(linkText);
        }
    }

    private static final class CompanyRow {
        final int id, applications;
        final String name;
        final SimpleStringProperty linkedinId;
        String savedLinkedinId;
        final SimpleBooleanProperty priority, blacklisted;
        CompanyRow(int id, String name, String linkedinId, int applications,
                   boolean priority, boolean blacklisted) {
            this.id = id; this.name = name; this.applications = applications;
            this.linkedinId = new SimpleStringProperty(linkedinId);
            this.savedLinkedinId = linkedinId;
            this.priority = new SimpleBooleanProperty(priority);
            this.blacklisted = new SimpleBooleanProperty(blacklisted);
        }
        BooleanProperty priorityProperty() { return priority; }
        BooleanProperty blacklistedProperty() { return blacklisted; }
        javafx.beans.property.StringProperty linkedinIdProperty() { return linkedinId; }
    }

    private static final class ApplicationRow {
        final int id; final String sourceUrl, title, company, date; final Integer companyId;
        String status;
        ApplicationRow(int id, String sourceUrl, String title, String company,
                       Integer companyId, String date, String status) {
            this.id=id; this.sourceUrl=sourceUrl; this.title=title; this.company=company;
            this.companyId=companyId; this.date=date; this.status=status;
        }
    }

    private record CollectedRow(String sourceId, String stage, String collector, String title,
                                String company, String location, String workplace, String salary,
                                String url, String text) { }
    private record ApplicationsData(List<String> statuses, List<ApplicationRow> rows) { }
    private record ConfigData(JsonNode rows, String limit) { }
}
