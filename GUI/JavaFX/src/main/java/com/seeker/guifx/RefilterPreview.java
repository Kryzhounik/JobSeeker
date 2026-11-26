package com.seeker.guifx;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.SelectionMode;
import javafx.scene.Cursor;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

final class RefilterPreview {
    private final Stage dialog = new Stage();

    RefilterPreview(Stage owner, List<RefilterCandidate> candidates, double width, double height,
                    Consumer<List<RefilterCandidate>> apply, Consumer<String> openUrl) {
        List<RefilterRow> rows = new ArrayList<>();
        candidates.forEach(candidate -> rows.add(new RefilterRow(candidate)));
        TableView<RefilterRow> table = new TableView<>(FXCollections.observableArrayList(rows));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        FillLastColumnResizePolicy.install(table);
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
        TableColumn<RefilterRow, String> sourceId = refilterColumn("Source ID", RefilterCandidate::sourceJobId);
        selected.setPrefWidth(36);
        sourceId.setPrefWidth(125);
        TableColumn<RefilterRow, String> title = refilterColumn("Title", RefilterCandidate::title);
        title.setPrefWidth(280);
        title.setCellFactory(column -> new TableCell<>() {
            private final TextField linkedText = new TextField();
            {
                linkedText.setEditable(false);
                linkedText.setFocusTraversable(true);
                linkedText.setCursor(Cursor.HAND);
                linkedText.getStyleClass().add("filter-link-cell");
                CellTextFieldWidth.fitToText(linkedText, this);
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                RefilterRow row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); return; }
                linkedText.setText(value);
                linkedText.setOnMouseClicked(event -> {
                    if (event.getClickCount() == 1 && linkedText.getSelectedText().isEmpty()) {
                        openUrl.accept(row.candidate.sourceUrl());
                    }
                });
                setGraphic(linkedText);
            }
        });
        TableColumn<RefilterRow, String> previous = refilterColumn("Previous", RefilterCandidate::previousStatus);
        previous.setPrefWidth(105);
        TableColumn<RefilterRow, String> action = refilterColumn("Action", RefilterCandidate::action);
        action.setPrefWidth(105);
        TableColumn<RefilterRow, String> fit = refilterColumn("Fit",
                candidate -> candidate.fit() == null ? "" : candidate.fit().toString());
        fit.setPrefWidth(60);
        TableColumn<RefilterRow, String> original = refilterColumn("Original", RefilterCandidate::original);
        original.setPrefWidth(440);
        TableColumn<RefilterRow, String> matched = refilterColumn("Match", RefilterCandidate::matched);
        matched.setPrefWidth(260);
        for (TableColumn<RefilterRow, String> column : List.of(
                sourceId, previous, action, fit)) {
            CopyableTextCell.install(column);
        }
        CopyableTextCell.installExpandable(original);
        CopyableTextCell.installExpandable(matched);
        table.getColumns().setAll(selected, sourceId, title, previous, action, fit, original, matched);
        Label count = new Label("Refilter candidates: " + rows.size());
        Button cancel = new Button("Cancel");
        dialog.initOwner(owner);
        dialog.initModality(javafx.stage.Modality.APPLICATION_MODAL);
        dialog.setTitle("Refilter detail");
        confirm.setOnAction(event -> {
            List<RefilterCandidate> chosen = rows.stream().filter(row -> row.selected.get())
                    .map(row -> row.candidate).toList();
            if (chosen.isEmpty()) return;
            dialog.close();
            apply.accept(chosen);
        });
        cancel.setOnAction(event -> dialog.close());
        HBox footer = new HBox(8, all, count, new javafx.scene.layout.Region(), cancel, confirm);
        HBox.setHgrow(footer.getChildren().get(2), javafx.scene.layout.Priority.ALWAYS);
        VBox content = new VBox(8, table, footer);
        content.setPadding(new javafx.geometry.Insets(10));
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        javafx.scene.Scene dialogScene = new javafx.scene.Scene(content, 1160, 540);
        dialogScene.getStylesheets().addAll(owner.getScene().getStylesheets());
        dialog.setScene(dialogScene);
        dialog.setWidth(width);
        dialog.setHeight(height);
    }

    private TableColumn<RefilterRow, String> refilterColumn(
            String title, java.util.function.Function<RefilterCandidate, String> value) {
        TableColumn<RefilterRow, String> column = new TableColumn<>(title);
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                value.apply(cell.getValue().candidate)));
        return column;
    }

    private static final class RefilterRow {
        final RefilterCandidate candidate;
        final SimpleBooleanProperty selected = new SimpleBooleanProperty(true);
        RefilterRow(RefilterCandidate candidate) { this.candidate = candidate; }
    }

    Stage window() { return dialog; }
    void show() { dialog.show(); }
}

