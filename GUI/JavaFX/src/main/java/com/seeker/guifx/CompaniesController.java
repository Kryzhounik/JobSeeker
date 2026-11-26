package com.seeker.guifx;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

final class CompaniesController {
    private final ClientData data;
    private final WindowSupport windows;
    private Stage companiesStage;
    @javafx.fxml.FXML private TableView<CompanyRow> companiesTable;
    @javafx.fxml.FXML private TextField linkedinField;
    @javafx.fxml.FXML private TextField nameField;
    @javafx.fxml.FXML private Button addButton;
    private List<CompanyRow> companyRows = new ArrayList<>();

    CompaniesController(ClientData data, WindowSupport windows) {
        this.data = data;
        this.windows = windows;
    }

    void refreshIfOpen() {
        if (companiesStage != null && companiesStage.isShowing()) refreshCompanies(null);
    }

    void showCompanies(Integer focusId) {
        try {
            if (companiesStage == null) createView();
            companiesStage.show();
            companiesStage.toFront();
            refreshCompanies(focusId);
        } catch (Exception error) {
            WindowSupport.showError("Companies load failed", error);
        }
    }

    private void createView() {
        companiesStage = windows.stage("Companies", 760, 620);
        companiesStage.setScene(new Scene(WindowSupport.loadView(this, "companies-view.fxml"), 760, 620));
        FillLastColumnResizePolicy.install(companiesTable);
        TableColumn<CompanyRow, Boolean> priority = new TableColumn<>("Priority");
        priority.setCellValueFactory(cell -> cell.getValue().priorityProperty());
        priority.setCellFactory(column -> flagCell(true));
        priority.setEditable(true);
        priority.setPrefWidth(75);
        priority.setOnEditCommit(event -> saveCompanyFlag(
                event.getRowValue(), event.getNewValue(), true
        ));
        companiesTable.getColumns().add(priority);
        TableColumn<CompanyRow, String> company = companyColumn();
        company.setPrefWidth(300);
        companiesTable.getColumns().add(company);
        TableColumn<CompanyRow, String> linkedin = new TableColumn<>("LinkedIn ID");
        linkedin.setCellValueFactory(cell -> cell.getValue().linkedinIdProperty());
        linkedin.setCellFactory(TextFieldTableCell.forTableColumn());
        linkedin.setOnEditCommit(event -> {
            CompanyRow row = event.getRowValue();
            String value = event.getNewValue().strip();
            windows.submit(() -> {
                data.setCompanyLinkedInId(row.id, value);
                return null;
            }, ignored -> {
                row.linkedinId.set(value);
                row.savedLinkedinId = value;
            }, error -> {
                row.linkedinId.set(row.savedLinkedinId);
                companiesTable.refresh();
                WindowSupport.showError("LinkedIn ID update failed", error);
            });
        });
        linkedin.setPrefWidth(145);
        companiesTable.getColumns().add(linkedin);
        TableColumn<CompanyRow, String> applications = applicationsColumn();
        applications.setPrefWidth(105);
        companiesTable.getColumns().add(applications);
        TableColumn<CompanyRow, Boolean> blacklisted = new TableColumn<>("Blacklisted");
        blacklisted.setCellValueFactory(cell -> cell.getValue().blacklistedProperty());
        blacklisted.setCellFactory(column -> flagCell(false));
        blacklisted.setEditable(true);
        blacklisted.setPrefWidth(95);
        blacklisted.setOnEditCommit(event -> saveCompanyFlag(
                event.getRowValue(), event.getNewValue(), false
        ));
        companiesTable.getColumns().add(blacklisted);
        windows.restoreTableSort(companiesTable, "company");

        Button add = addButton;
        add.setOnAction(event -> {
            String name = nameField.getText().strip();
            if (name.isEmpty()) {
                nameField.requestFocus();
                return;
            }
            String linkedinId = linkedinField.getText().strip();
            add.setDisable(true);
            windows.submit(() -> data.createCompany(name, linkedinId), id -> {
                add.setDisable(false);
                nameField.clear();
                linkedinField.clear();
                refreshCompanies(id);
            }, error -> {
                add.setDisable(false);
                WindowSupport.showError("Company could not be added", error);
            });
        });
    }

    private void refreshCompanies(Integer focusId) {
        windows.submit(data::loadCompanies, rows -> {
            companyRows = new ArrayList<>();
            for (CompanyDto row : rows) {
                companyRows.add(new CompanyRow(
                        row.id(), row.name(), row.linkedinId(), row.applicationCount(),
                        row.priority() != 0, row.blacklisted() != 0
                ));
            }
            companiesTable.setItems(FXCollections.observableArrayList(companyRows));
            if (focusId != null) {
                companyRows.stream().filter(row -> row.id == focusId).findFirst().ifPresent(row -> {
                    companiesTable.getSelectionModel().select(row);
                    companiesTable.scrollTo(row);
                });
            }
        }, error -> WindowSupport.showError("Companies load failed", error));
    }

    private void saveCompanyFlag(CompanyRow row, boolean value, boolean priority) {
        boolean previous = (priority ? row.priority : row.blacklisted).get();
        windows.submit(() -> {
            if (priority) data.setCompanyPriority(row.id, value);
            else data.setCompanyBlacklisted(row.id, value);
            return null;
        }, ignored -> {
            (priority ? row.priority : row.blacklisted).set(value);
        }, error -> {
            (priority ? row.priority : row.blacklisted).set(previous);
            companiesTable.refresh();
            WindowSupport.showError("Company update failed", error);
        });
    }

    private TableCell<CompanyRow, Boolean> flagCell(boolean isPriority) {
        return new TableCell<>() {
            private final CheckBox check = new CheckBox();
            {
                check.setOnAction(event -> {
                    CompanyRow row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) saveCompanyFlag(row, check.isSelected(), isPriority);
                });
            }
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                setGraphic(empty || value == null ? null : check);
                if (!empty && value != null) check.setSelected(value);
            }
        };
    }

    private TableColumn<CompanyRow, String> companyColumn() {
        TableColumn<CompanyRow, String> column = new TableColumn<>("Company");
        column.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().name));
        CopyableTextCell.install(column);
        return column;
    }

    private TableColumn<CompanyRow, String> applicationsColumn() {
        TableColumn<CompanyRow, String> column = new TableColumn<>("Applications");
        column.setCellValueFactory(cell -> new SimpleStringProperty(
                Integer.toString(cell.getValue().applications)));
        column.setComparator(java.util.Comparator.comparingInt(Integer::parseInt));
        CopyableTextCell.install(column);
        return column;
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
}
