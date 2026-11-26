package com.seeker.guifx;

import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.*;
import java.util.function.*;

final class JobsTableView {
    record Actions(Consumer<Integer> company, Consumer<Integer> application,
                   Consumer<String> added, Consumer<String> reason, Consumer<String> blacklist) { }

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter FILTER_DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private final TableView<JobRecord> jobsTable;
    private final Map<String, TableColumn<JobRecord, String>> columns = new LinkedHashMap<>();
    private final Actions actions;
    private final Predicate<JobRecord> isParent;
    private final Supplier<Map<String, String>> reasons;
    private final Runnable sortChanged;

    @SuppressWarnings("unchecked")
    JobsTableView(TableView<JobRecord> jobsTable, Actions actions, Predicate<JobRecord> isParent,
                  Supplier<Map<String, String>> reasons, Runnable sortChanged) {
        this.jobsTable = jobsTable;
        this.actions = actions;
        this.isParent = isParent;
        this.reasons = reasons;
        this.sortChanged = sortChanged;
        for (TableColumn<JobRecord, ?> column : jobsTable.getColumns()) {
            columns.put(column.getId(), (TableColumn<JobRecord, String>) column);
        }
        configure();
    }

    TableColumn<JobRecord, String> column(String key) { return columns.get(key + "Column"); }

    private void configure() {
        TableColumn<JobRecord, String> scoreColumn = column("score");
        TableColumn<JobRecord, String> fitColumn = column("fit");
        TableColumn<JobRecord, String> interestColumn = column("interest");
        TableColumn<JobRecord, String> statusColumn = column("status");
        TableColumn<JobRecord, String> remoteColumn = column("remote");
        TableColumn<JobRecord, String> relocationColumn = column("relocation");
        TableColumn<JobRecord, String> locationColumn = column("location");
        TableColumn<JobRecord, String> companyColumn = column("company");
        TableColumn<JobRecord, String> titleColumn = column("title");
        TableColumn<JobRecord, String> roleColumn = column("role");
        TableColumn<JobRecord, String> seniorityColumn = column("seniority");
        TableColumn<JobRecord, String> languageColumn = column("language");
        TableColumn<JobRecord, String> salaryColumn = column("salary");
        TableColumn<JobRecord, String> addedColumn = column("added");
        TableColumn<JobRecord, String> reasonCodeColumn = column("reasonCode");
        TableColumn<JobRecord, String> reasonColumn = column("reason");
        FillLastColumnResizePolicy.install(jobsTable);
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
            { CellTextFieldWidth.fitToText(linkedText, this); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                JobRecord row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null || value == null || value.isBlank() || row.companyId().isBlank()) {
                    setGraphic(null);
                    return;
                }
                linkedText.setText(value);
                linkedText.setOnMouseClicked(event -> {
                    if (isLinkClick(linkedText, event.getClickCount())) {
                        try { actions.company().accept(Integer.parseInt(row.companyId())); }
                        catch (NumberFormatException ignored) { }
                    }
                });
                setGraphic(linkedText);
            }
        });
        bind(titleColumn, JobRecord::title);
        TitleTextCell.install(titleColumn, actions.blacklist(), row -> row.duplicateParent() != null);
        jobsTable.setRowFactory(table -> new javafx.scene.control.TableRow<>() {
            @Override protected void updateItem(JobRecord job, boolean empty) {
                super.updateItem(job, empty);
                boolean parent = !empty && job != null && isParent.test(job);
                pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("duplicate-parent"),
                        parent);
                setTooltip(parent ? new Tooltip("Referenced by the duplicates below") : null);
            }
        });
        bind(roleColumn, JobRecord::role);
        bind(seniorityColumn, JobRecord::seniority);
        bind(languageColumn, JobRecord::language);
        bind(salaryColumn, JobRecord::salary);
        bind(addedColumn, row -> formatDate(row.addedAt()));
        bind(reasonCodeColumn, JobRecord::reasonCode);
        bind(reasonColumn, JobRecord::reason);

        for (TableColumn<JobRecord, String> column : List.of(
                scoreColumn, fitColumn, interestColumn, statusColumn, remoteColumn,
                relocationColumn, locationColumn,
                roleColumn, seniorityColumn, languageColumn, salaryColumn,
                reasonColumn
        )) {
            CopyableTextCell.install(column);
        }
        FilterLinkCell.install(
                addedColumn,
                row -> formatDate(row.addedAt()),
                actions.added()
        );
        FilterLinkCell.install(reasonCodeColumn, JobRecord::reasonCode, actions.reason(),
                code -> reasons.get().getOrDefault(code, ""));
        statusColumn.setCellFactory(column -> new TableCell<>() {
            private final TextField linkedText = linkedTextField();
            private final TextField copyable = new TextField();
            {
                CellTextFieldWidth.fitToText(linkedText, this);
                copyable.setEditable(false);
                copyable.setFocusTraversable(true);
                copyable.getStyleClass().add("copyable-cell");
                copyable.setPadding(javafx.geometry.Insets.EMPTY);
                CellTextFieldWidth.fitToText(copyable, this);
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                JobRecord row = empty || getTableRow() == null ? null : getTableRow().getItem();
                if (row == null) { setGraphic(null); return; }
                if ("Applied".equalsIgnoreCase(value) && !row.applicationId().isBlank()) {
                    linkedText.setText(value);
                    linkedText.setOnMouseClicked(event -> {
                        if (isLinkClick(linkedText, event.getClickCount())) {
                            try { actions.application().accept(Integer.parseInt(row.applicationId())); }
                            catch (NumberFormatException ignored) { }
                        }
                    });
                    setGraphic(linkedText);
                } else {
                    copyable.setText(value);
                    setGraphic(copyable);
                }
            }
        });

        scoreColumn.setComparator(numericComparator());
        fitColumn.setComparator(numericComparator());
        interestColumn.setComparator(numericComparator());
        addedColumn.setComparator(Comparator.comparing(
                JobsTableView::parseDisplayDate,
                Comparator.nullsFirst(Comparator.naturalOrder())
        ));

        jobsTable.getSelectionModel().setSelectionMode(
                javafx.scene.control.SelectionMode.MULTIPLE
        );
        jobsTable.setSortPolicy(table -> {
            JobRows.sort(table.getItems(), table.getComparator());
            sortChanged.run();
            return true;
        });
    }


    private static TextField linkedTextField() {
        TextField field = new TextField();
        field.setEditable(false);
        field.setFocusTraversable(true);
        field.setCursor(Cursor.HAND);
        field.getStyleClass().add("filter-link-cell");
        return field;
    }

    private static boolean isLinkClick(TextField field, int clickCount) {
        return clickCount == 1 && field.getSelectedText().isEmpty();
    }


    private static void bind(
            TableColumn<JobRecord, String> column,
            java.util.function.Function<JobRecord, String> value
    ) {
        column.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                safe(value.apply(cell.getValue()))
        ));
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

    static String formatDate(String value) {
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


    private static String safe(String value) { return value == null ? "" : value; }
}

