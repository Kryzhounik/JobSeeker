package com.seeker.guifx;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.Consumer;

final class CollectedJobsController {
    private final ClientData data;
    private final WindowSupport windows;
    private final Consumer<String> addTitleToBlacklist;
    private final SettingsStore settingsStore;
    private Stage collectedStage;
    @javafx.fxml.FXML private TableView<CollectedJobDto> table;
    @javafx.fxml.FXML private ComboBox<String> stageFilter;
    @javafx.fxml.FXML private TextArea text;
    @javafx.fxml.FXML private Button refresh;
    @javafx.fxml.FXML private Button open;

    CollectedJobsController(ClientData data, WindowSupport windows,
                            Consumer<String> addTitleToBlacklist, SettingsStore settingsStore) {
        this.data = data;
        this.windows = windows;
        this.addTitleToBlacklist = addTitleToBlacklist;
        this.settingsStore = settingsStore;
    }

    void refreshIfOpen() {
        if (collectedStage != null && collectedStage.isShowing()) showCollected();
    }

    void showCollected() {
        windows.submit(data::loadCollectedJobs, this::buildCollected,
                error -> WindowSupport.showError("Collected jobs load failed", error));
    }

    private void buildCollected(List<CollectedJobDto> rows) {
        javafx.scene.Parent view = WindowSupport.loadView(this, "collected-jobs-view.fxml");
        TableView<CollectedJobDto> table = this.table;
        ComboBox<String> stageFilter = this.stageFilter;
        table.setItems(FXCollections.observableArrayList(rows));
        FillLastColumnResizePolicy.install(table);
        TableColumn<CollectedJobDto, String> titleColumn = collectedColumn("Title", CollectedJobDto::title);
        titleColumn.setPrefWidth(300);
        TitleTextCell.install(titleColumn, addTitleToBlacklist);
        TableColumn<CollectedJobDto, String> sourceId = collectedColumn("Source ID", CollectedJobDto::sourceJobId);
        sourceId.setPrefWidth(130);
        TableColumn<CollectedJobDto, String> stage = collectedColumn("Stage", CollectedJobDto::processingStatus);
        stage.setPrefWidth(155);
        TableColumn<CollectedJobDto, String> collector = collectedColumn("Collector", CollectedJobDto::collectionMethod);
        collector.setPrefWidth(105);
        TableColumn<CollectedJobDto, String> company = collectedColumn("Company", CollectedJobDto::company);
        company.setPrefWidth(190);
        TableColumn<CollectedJobDto, String> location = collectedColumn("Location", CollectedJobDto::collectedLocation);
        location.setPrefWidth(230);
        TableColumn<CollectedJobDto, String> workplace = collectedColumn("Workplace", CollectedJobDto::collectedWorkplace);
        workplace.setPrefWidth(100);
        TableColumn<CollectedJobDto, String> salary = collectedColumn("Salary", CollectedJobDto::collectedSalary);
        salary.setPrefWidth(135);
        TableColumn<CollectedJobDto, String> url = collectedColumn("URL", CollectedJobDto::sourceUrl);
        url.setPrefWidth(280);
        table.getColumns().setAll(
                sourceId,
                stage,
                collector,
                titleColumn,
                company,
                location,
                workplace,
                salary,
                url
        );
        windows.restoreTableSort(table, "collected");
        List<String> stages = rows.stream().map(CollectedJobDto::processingStatus).distinct().sorted().toList();
        stageFilter.getItems().add("All");
        stageFilter.getItems().addAll(stages);
        String savedStage = settingsStore.collectedStage();
        stageFilter.setValue(stageFilter.getItems().contains(savedStage) ? savedStage : "All");
        java.util.function.Consumer<String> applyStage = value -> {
            table.setItems(FXCollections.observableArrayList(rows.stream().filter(row ->
                    "All".equals(value) || row.processingStatus().equals(value)).toList()));
            table.sort();
        };
        applyStage.accept(stageFilter.getValue());
        stageFilter.setOnAction(event -> {
            applyStage.accept(stageFilter.getValue());
            try { settingsStore.saveCollectedStage(stageFilter.getValue()); }
            catch (java.io.IOException error) { WindowSupport.showError("Settings save failed", error); }
        });
        TextArea text = this.text;
        table.getSelectionModel().selectedItemProperty().addListener((obs, old, row) ->
                text.setText(row == null ? "" : row.readableText()));
        table.setRowFactory(view -> {
            var row = new javafx.scene.control.TableRow<CollectedJobDto>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()
                        && !TitleTextCell.isTitleTarget(event.getTarget())) {
                    WindowSupport.openUrl(row.getItem().sourceUrl());
                }
            });
            return row;
        });
        refresh.setOnAction(event -> showCollected());
        open.setOnAction(event -> {
            CollectedJobDto row = table.getSelectionModel().getSelectedItem();
            if (row != null) WindowSupport.openUrl(row.sourceUrl());
        });
        if (collectedStage == null) collectedStage = windows.stage("Collected jobs", 1300, 760);
        collectedStage.setScene(new Scene(view, 1300, 760));
        collectedStage.show();
        collectedStage.toFront();
    }

    private TableColumn<CollectedJobDto, String> collectedColumn(
            String title, java.util.function.Function<CollectedJobDto, String> value
    ) {
        TableColumn<CollectedJobDto, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new SimpleStringProperty(value.apply(cell.getValue())));
        CopyableTextCell.install(column);
        return column;
    }
}
