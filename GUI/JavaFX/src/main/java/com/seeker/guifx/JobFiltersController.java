package com.seeker.guifx;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class JobFiltersController {
    private static final DateTimeFormatter FILTER_DATE = DateTimeFormatter
            .ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    @FXML private Button refreshButton;
    @FXML private Button clearButton;
    @FXML private HBox statusFilters;
    @FXML private CheckBox showZeroCheck;
    @FXML private TextField idFilterField;
    @FXML private TextField dateFilterField;
    @FXML private TextField reasonFilterField;
    private final Map<String, CheckBox> statusChecks = new LinkedHashMap<>();
    private final CheckBox unavailableCheck = new CheckBox("Unavailable");
    private SettingsStore settingsStore;
    private Runnable changed;
    private Consumer<String> message;

    void configure(SettingsStore settingsStore, Runnable changed, Consumer<String> message) {
        this.settingsStore = settingsStore;
        this.changed = changed;
        this.message = message;
        refreshButton.setOnAction(event -> changed.run());
        clearButton.setOnAction(event -> clearTextFilters());
        idFilterField.setOnAction(event -> changed.run());
        dateFilterField.setOnAction(event -> changed.run());
        reasonFilterField.setOnAction(event -> changed.run());
        refreshButton.setDisable(true);
    }

    void loadStatuses(List<String> statuses) {
        statusFilters.getChildren().clear();
        statusChecks.clear();
        for (String status : statuses) {
            if (status.equalsIgnoreCase("Closed")) continue;
            CheckBox checkBox = new CheckBox(status);
            checkBox.setSelected(settingsStore.statusEnabled(status));
            checkBox.setOnAction(event -> {
                saveStatusFilters();
                changed.run();
            });
            statusChecks.put(status, checkBox);
            statusFilters.getChildren().add(checkBox);
        }
        unavailableCheck.setSelected(settingsStore.showUnavailable());
        unavailableCheck.setOnAction(event -> {
            saveStatusFilters();
            changed.run();
        });
        statusFilters.getChildren().add(unavailableCheck);
        showZeroCheck.setSelected(settingsStore.showZero());
        showZeroCheck.setOnAction(event -> {
            saveStatusFilters();
            changed.run();
        });
    }

    JobFilters snapshot() {
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
        return new JobFilters(
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
    }

    void setLoading(boolean loading) {
        refreshButton.setDisable(loading);
        clearButton.setDisable(loading);
        statusFilters.setDisable(loading);
        showZeroCheck.setDisable(loading);
        idFilterField.setDisable(loading);
        dateFilterField.setDisable(loading);
        reasonFilterField.setDisable(loading);
    }

    boolean prepareForJob(JobDetail detail) {
        CheckBox statusCheck = statusChecks.get(detail.status());
        if (statusCheck == null) {
            message.accept("Unknown vacancy status: " + detail.status());
            return false;
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
        idFilterField.setText(detail.id());
        return true;
    }

    private void clearTextFilters() {
        idFilterField.clear();
        dateFilterField.clear();
        reasonFilterField.clear();
        changed.run();
    }

    void filterFromDate(String date) {
        dateFilterField.setText(date);
        changed.run();
    }

    void filterByReason(String reason) {
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
        changed.run();
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
            message.accept("Settings save failed: " + error.getMessage());
        }
    }
}

