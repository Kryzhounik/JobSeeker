package com.seeker.guifx;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

final class ApplicationsController {
    private final ClientData data;
    private final WindowSupport windows;
    private final Consumer<Integer> openJob;
    private final Consumer<Integer> showCompanies;
    private Stage applicationsStage;
    @javafx.fxml.FXML private TableView<ApplicationRow> table;

    ApplicationsController(ClientData data, WindowSupport windows, Consumer<Integer> openJob,
                           Consumer<Integer> showCompanies) {
        this.data = data;
        this.windows = windows;
        this.openJob = openJob;
        this.showCompanies = showCompanies;
    }

    void refreshIfOpen() {
        if (applicationsStage != null && applicationsStage.isShowing()) showApplications(null);
    }

    void showApplications(Integer focusId) {
        windows.submit(() -> {
            List<String> statuses = data.loadApplicationStatuses();
            List<ApplicationRow> rows = new ArrayList<>();
            for (ApplicationDto row : data.loadApplications()) {
                rows.add(new ApplicationRow(row.id(), row.jobId(), row.title(), row.company(),
                        row.companyId(), row.appliedAt(), row.status()));
            }
            return new ApplicationsData(statuses, rows);
        }, loaded -> buildApplications(loaded, focusId),
                error -> WindowSupport.showError("Applications load failed", error));
    }

    private void buildApplications(ApplicationsData loaded, Integer focusId) {
        List<String> statuses = loaded.statuses;
        List<ApplicationRow> rows = loaded.rows;
        javafx.scene.Parent view = WindowSupport.loadView(this, "applications-view.fxml");
        TableView<ApplicationRow> table = this.table;
        table.setItems(FXCollections.observableArrayList(rows));
        FillLastColumnResizePolicy.install(table);
        TableColumn<ApplicationRow, String> title = new TableColumn<>("Title");
        title.setPrefWidth(380);
        title.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().title));
        title.setCellFactory(column -> new WindowSupport.HyperlinkCell<>(row -> row.title, row ->
                openJob.accept(row.jobId)));
        TableColumn<ApplicationRow, String> company = new TableColumn<>("Company");
        company.setPrefWidth(260);
        company.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().company));
        company.setCellFactory(column -> new WindowSupport.HyperlinkCell<>(row -> row.company, row -> {
            if (row.companyId != null) showCompanies.accept(row.companyId);
        }));
        TableColumn<ApplicationRow, String> date = new TableColumn<>("Date");
        date.setPrefWidth(120);
        date.setCellValueFactory(cell -> new SimpleStringProperty(WindowSupport.displayDate(cell.getValue().date)));
        date.setComparator(java.util.Comparator.comparing(
                WindowSupport::parseDisplayDate,
                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
        CopyableTextCell.install(date);
        TableColumn<ApplicationRow, String> status = new TableColumn<>("Status");
        status.setPrefWidth(210);
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
                    windows.submit(() -> {
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
                        WindowSupport.showError("Application update failed", error);
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
        windows.restoreTableSort(table, "application");
        if (focusId != null) rows.stream().filter(row -> row.id == focusId).findFirst().ifPresent(row -> {
            table.getSelectionModel().select(row);
            table.scrollTo(row);
        });
        if (applicationsStage == null) applicationsStage = windows.stage("Applications", 1050, 650);
        applicationsStage.setScene(new Scene(view, 1050, 650));
        applicationsStage.show();
        applicationsStage.toFront();
    }

    private static final class ApplicationRow {
        final int id, jobId; final String title, company, date; final Integer companyId;
        String status;
        ApplicationRow(int id, int jobId, String title, String company,
                       Integer companyId, String date, String status) {
            this.id=id; this.jobId=jobId; this.title=title; this.company=company;
            this.companyId=companyId; this.date=date; this.status=status;
        }
    }

    private record ApplicationsData(List<String> statuses, List<ApplicationRow> rows) { }
}
