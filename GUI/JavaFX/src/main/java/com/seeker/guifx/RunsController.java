package com.seeker.guifx;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.function.Consumer;

final class RunsController {
    private final ClientData data;
    private final WindowSupport windows;
    private final Consumer<CollectionRunDto> resumeRun;
    private final javafx.beans.property.BooleanProperty workflowRunning = new SimpleBooleanProperty();
    private Stage runsStage;
    @javafx.fxml.FXML private TableView<CollectionRunDto> runsTable;
    @javafx.fxml.FXML private Button runsRefresh;
    @javafx.fxml.FXML private TextArea details;
    @javafx.fxml.FXML private HBox toolbar;
    @javafx.fxml.FXML private BorderPane content;
    private Label runsCount;

    RunsController(ClientData data, WindowSupport windows, Consumer<CollectionRunDto> resumeRun) {
        this.data = data;
        this.windows = windows;
        this.resumeRun = resumeRun;
    }

    void setWorkflowRunning(boolean running) { workflowRunning.set(running); }

    void refreshIfOpen() {
        if (runsStage != null && runsStage.isShowing()) refreshRuns();
    }

    void showRuns() {
        if (runsStage == null) createView();
        runsStage.show();
        runsStage.toFront();
        refreshRuns();
    }

    private void createView() {
        runsStage = windows.stage("Runs", 1250, 620);
        javafx.scene.Parent view = WindowSupport.loadView(this, "runs-view.fxml");
        FillLastColumnResizePolicy.install(runsTable);
        TableColumn<CollectionRunDto, String> id = runColumn("Run ID", CollectionRunDto::runId, 310);
        TableColumn<CollectionRunDto, String> status = runColumn("Status", CollectionRunDto::status, 85);
        TableColumn<CollectionRunDto, String> stage = runColumn("Stage", CollectionRunDto::stage, 135);
        TableColumn<CollectionRunDto, String> started = runColumn("Started", CollectionRunDto::startedAt, 155);
        TableColumn<CollectionRunDto, String> finished = runColumn("Finished", CollectionRunDto::finishedAt, 155);
        TableColumn<CollectionRunDto, String> accepted = runColumn("Accepted",
                row -> Integer.toString(row.acceptedCount()), 80);
        accepted.setComparator(java.util.Comparator.comparingInt(Integer::parseInt));
        TableColumn<CollectionRunDto, String> reason = runColumn("Stop reason", CollectionRunDto::stopReason, 180);
        runsTable.getColumns().setAll(id, status, stage, started, finished, accepted, reason);
        runsTable.setPlaceholder(new Label("No runs"));
        windows.restoreTableSort(runsTable, "runs");
        runsTable.getSelectionModel().selectedItemProperty().addListener((obs, old, row) ->
                details.setText(row == null ? "" : "Collection status: " + row.collectionStatus()
                        + "\nCollection finished: " + row.collectionFinishedAt()
                        + (row.message().isBlank() ? "" : "\n\n" + row.message())));
        runsRefresh.setOnAction(event -> refreshRuns());
        runsCount = new Label();
        toolbar.getChildren().add(
                resumeButton(runsTable, workflowRunning.or(runsRefresh.disableProperty()), resumeRun));
        content.setBottom(new WindowStatusBar(runsCount));
        runsStage.setScene(new Scene(view, 1250, 620));
    }

    static Button resumeButton(TableView<CollectionRunDto> table,
            javafx.beans.value.ObservableBooleanValue busy, Consumer<CollectionRunDto> action) {
        Button resume = new Button("Resume");
        resume.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            CollectionRunDto row = table.getSelectionModel().getSelectedItem();
            return busy.get() || row == null || "running".equals(row.status())
                    || "complete".equals(row.status());
        }, busy, table.getSelectionModel().selectedItemProperty()));
        resume.setOnAction(event -> action.accept(table.getSelectionModel().getSelectedItem()));
        return resume;
    }

    private void refreshRuns() {
        runsRefresh.setDisable(true);
        runsCount.setText("Loading...");
        CollectionRunDto selected = runsTable.getSelectionModel().getSelectedItem();
        windows.submit(data::loadCollectionRuns, rows -> {
            runsTable.setItems(FXCollections.observableArrayList(rows));
            runsTable.sort();
            if (selected != null) rows.stream().filter(row -> row.runId().equals(selected.runId()))
                    .findFirst().ifPresent(row -> runsTable.getSelectionModel().select(row));
            runsRefresh.setDisable(false);
            runsCount.setText(rows.size() + " runs");
        }, error -> {
            runsRefresh.setDisable(false);
            runsCount.setText("Load failed");
            WindowSupport.showError("Runs load failed", error);
        });
    }

    private TableColumn<CollectionRunDto, String> runColumn(String title,
            java.util.function.Function<CollectionRunDto, String> value, double width) {
        TableColumn<CollectionRunDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        column.setPrefWidth(width);
        CopyableTextCell.install(column);
        return column;
    }
}
