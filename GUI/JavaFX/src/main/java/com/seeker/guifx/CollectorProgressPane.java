package com.seeker.guifx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.LinkedHashMap;
import java.util.Map;

final class CollectorProgressPane {
    private final ToggleButton dock;
    private final Tooltip dockTooltip = new Tooltip();
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final TextArea details = new TextArea();
    private int limit;
    private int accepted;
    private String outcome = "";

    CollectorProgressPane(StackPane host, ToggleButton dock) {
        this.dock = dock;
        dock.setTooltip(dockTooltip);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(155), values);
        for (String name : new String[]{"Run ID", "Stage", "Location", "Search", "Page",
                "Cards on page", "Processed", "Accepted / limit", "Skipped by preview",
                "Rejected by content", "Open failures", "Incomplete data", "Skipped at limit"}) {
            TextField value = new TextField();
            value.setEditable(false);
            value.setMaxWidth(Double.MAX_VALUE);
            int row = fields.size();
            fields.put(name, value);
            grid.addRow(row, new Label(name), value);
        }
        details.setEditable(false);
        details.setWrapText(true);
        details.setPrefRowCount(5);
        VBox content = new VBox(8, grid, new Label("Details"), details);
        content.setPadding(new Insets(12));
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMinHeight(0);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button minimize = new Button("\u2212");
        minimize.setTooltip(new Tooltip("Minimize collection progress"));
        minimize.setOnAction(event -> dock.setSelected(false));
        HBox header = new HBox(8, new Label("Collect"), spacer, minimize);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 8, 5, 10));
        header.getStyleClass().add("collector-progress-header");
        VBox panel = new VBox(header, scroll);
        panel.getStyleClass().add("collector-progress");
        panel.setPrefSize(570, 620);
        panel.setMinSize(0, 0);
        panel.setMaxWidth(570);
        panel.maxHeightProperty().bind(Bindings.min(620,
                Bindings.max(0, host.heightProperty().subtract(16))));
        panel.visibleProperty().bind(dock.selectedProperty());
        panel.managedProperty().bind(panel.visibleProperty());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        StackPane.setAlignment(panel, Pos.TOP_RIGHT);
        StackPane.setMargin(panel, new Insets(8));
        host.getChildren().add(panel);
    }

    void begin() {
        fields.values().forEach(field -> field.clear());
        details.clear();
        limit = 0;
        accepted = 0;
        outcome = "";
        set("Stage", "Starting");
        updateDock();
        dock.setVisible(true);
        dock.setManaged(true);
        dock.setSelected(true);
    }

    void update(CollectorRunner.Progress progress) {
        if (!progress.runId().isBlank()) set("Run ID", progress.runId());
        if (progress.limit() > 0) limit = progress.limit();
        set("Stage", stageName(progress.stage()));
        set("Location", progress.location());
        set("Search", switch (progress.search()) {
            case "account_remote" -> "AccountRemote";
            case "priority" -> "Priority companies";
            case "general" -> "General";
            case "seed" -> "Session seed";
            default -> progress.search();
        });
        set("Page", progress.page() > 0 ? Integer.toString(progress.page()) : "");
        set("Cards on page", Integer.toString(progress.cards()));
        showCounts(progress.accepted(), progress.outcomes());
        details.setText(switch (progress.message()) {
            case "limit_reached" -> "Accepted-vacancy limit reached.";
            case "plan_exhausted" -> "Search plan exhausted.";
            default -> progress.message();
        });
    }

    void complete(CollectorRunner.Result result) {
        if (!result.runId().isBlank()) set("Run ID", result.runId());
        set("Stage", stageName(result.status()));
        showCounts(result.accepted(), result.outcomes());
        if (!result.message().isBlank()) details.setText(result.message());
        outcome = "complete".equals(result.status()) ? "done" : "stopped";
        updateDock();
    }

    void fail(Throwable error) {
        set("Stage", "Failed");
        details.setText(error == null ? "Unknown error" : String.valueOf(error.getMessage()));
        outcome = "error";
        updateDock();
    }

    private void showCounts(int accepted, Map<String, Integer> outcomes) {
        this.accepted = accepted;
        set("Accepted / limit", accepted + (limit > 0 ? " / " + limit : ""));
        int processed = outcomes.values().stream().mapToInt(Integer::intValue).sum()
                - outcomes.getOrDefault("not_processed_due_to_limit", 0);
        set("Processed", Integer.toString(processed));
        setCount("Skipped by preview", outcomes, "preview_filtered");
        setCount("Rejected by content", outcomes, "content_filtered");
        setCount("Open failures", outcomes, "open_failed");
        setCount("Incomplete data", outcomes, "incomplete_raw");
        setCount("Skipped at limit", outcomes, "not_processed_due_to_limit");
        updateDock();
    }

    private void updateDock() {
        dock.setText("Collect: " + accepted + (limit > 0 ? "/" + limit : "")
                + (outcome.isBlank() ? "" : " (" + outcome + ")"));
        dockTooltip.setText("Collection progress: " + fields.get("Stage").getText()
                + "\n" + fields.get("Run ID").getText());
    }

    private void setCount(String name, Map<String, Integer> outcomes, String key) {
        set(name, Integer.toString(outcomes.getOrDefault(key, 0)));
    }

    private void set(String name, String value) {
        fields.get(name).setText(value);
    }

    private static String stageName(String value) {
        return switch (value) {
            case "starting" -> "Starting";
            case "starting_browser" -> "Starting browser";
            case "checking_session" -> "Checking LinkedIn session";
            case "opening_seed" -> "Preparing search session";
            case "opening_search" -> "Opening search";
            case "reading_cards" -> "Reading search results";
            case "filtering_preview" -> "Filtering preview";
            case "opening_job" -> "Loading vacancy";
            case "filtering_content" -> "Saving and filtering content";
            case "processing" -> "Processing vacancies";
            case "complete" -> "Complete";
            case "login_required" -> "LinkedIn login required";
            case "blocked" -> "Stopped with error";
            default -> value;
        };
    }
}
