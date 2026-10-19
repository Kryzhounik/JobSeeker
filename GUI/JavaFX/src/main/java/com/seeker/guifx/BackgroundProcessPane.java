package com.seeker.guifx;

import javafx.beans.binding.Bindings;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;

abstract class BackgroundProcessPane {
    enum State {
        RUNNING("Running", "", "running"),
        WAITING("Waiting", "waiting", "waiting"),
        FAILED("Failed", "error", "failed"),
        COMPLETED("Complete", "done", "completed");

        private final String label;
        private final String suffix;
        private final PseudoClass style;

        State(String label, String suffix, String style) {
            this.label = label;
            this.suffix = suffix;
            this.style = PseudoClass.getPseudoClass(style);
        }
    }

    record Area(StackPane overlay, HBox docks, ToggleGroup selection) {
        Area(StackPane overlay, HBox docks) {
            this(overlay, docks, new ToggleGroup());
        }
    }

    private final String title;
    private final ToggleButton dock = new ToggleButton();
    private final HBox badge = new HBox();
    private final Button dismissDock = new Button("\u00d7");
    private final Button dismissPanel = new Button("\u00d7");
    private final Tooltip tooltip = new Tooltip();
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final TextArea details = new TextArea();
    private State state = State.RUNNING;
    private String summary = "";
    private String context = "";

    protected BackgroundProcessPane(Area area, String title, String... fieldNames) {
        this.title = title;
        dock.setTooltip(tooltip);
        dock.setToggleGroup(area.selection());
        dock.getStyleClass().add("background-process-toggle");
        dock.setMinWidth(0);
        dock.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(dock, Priority.ALWAYS);
        dismissDock.getStyleClass().add("background-process-dismiss");
        dismissDock.setTooltip(new Tooltip("Dismiss finished process"));
        dismissDock.setOnAction(event -> dismiss());
        dismissPanel.setTooltip(new Tooltip("Dismiss finished process"));
        dismissPanel.setOnAction(event -> dismiss());
        badge.getChildren().addAll(dock, dismissDock);
        badge.setAlignment(Pos.CENTER_LEFT);
        badge.getStyleClass().add("background-process-dock");
        badge.setMinWidth(100);
        badge.setPrefWidth(210);
        badge.setMaxWidth(210);
        badge.setVisible(false);
        badge.setManaged(false);
        area.docks().getChildren().add(badge);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);
        ColumnConstraints values = new ColumnConstraints();
        values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(155), values);
        for (String name : fieldNames) {
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
        minimize.setTooltip(new Tooltip("Minimize progress"));
        minimize.setOnAction(event -> dock.setSelected(false));
        HBox header = new HBox(8, new Label(title), spacer, minimize, dismissPanel);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(5, 8, 5, 10));
        header.getStyleClass().add("background-process-header");
        VBox panel = new VBox(header, scroll);
        panel.getStyleClass().add("background-process");
        panel.setPrefSize(570, 620);
        panel.setMinSize(0, 0);
        panel.setMaxWidth(570);
        panel.maxHeightProperty().bind(Bindings.min(620,
                Bindings.max(0, area.overlay().heightProperty().subtract(16))));
        panel.visibleProperty().bind(dock.selectedProperty());
        panel.managedProperty().bind(panel.visibleProperty());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        StackPane.setAlignment(panel, Pos.TOP_RIGHT);
        StackPane.setMargin(panel, new Insets(8));
        area.overlay().getChildren().add(panel);
        setProcessState(State.RUNNING);
    }

    protected final void beginProcess() {
        fields.values().forEach(TextField::clear);
        details.clear();
        summary = "";
        context = "";
        setProcessState(State.RUNNING);
        badge.setVisible(true);
        badge.setManaged(true);
        dock.setSelected(true);
    }

    protected final void setProcessState(State state) {
        this.state = state;
        for (State candidate : State.values()) {
            boolean active = candidate == state;
            dock.pseudoClassStateChanged(candidate.style, active);
            badge.pseudoClassStateChanged(candidate.style, active);
        }
        boolean finished = state == State.COMPLETED || state == State.FAILED;
        dismissDock.setVisible(finished);
        dismissDock.setManaged(finished);
        dismissPanel.setVisible(finished);
        dismissPanel.setManaged(finished);
        refreshDock();
    }

    private void dismiss() {
        if (state != State.COMPLETED && state != State.FAILED) return;
        dock.setSelected(false);
        badge.setVisible(false);
        badge.setManaged(false);
    }

    protected final void setSummary(String summary) {
        this.summary = summary;
        refreshDock();
    }

    protected final void setContext(String context) {
        this.context = context;
        refreshDock();
    }

    protected final void setField(String name, String value) {
        fields.get(name).setText(value);
    }

    protected final String fieldText(String name) {
        return fields.get(name).getText();
    }

    protected final void setDetails(String text) {
        details.setText(text);
    }

    private void refreshDock() {
        dock.setText(title + (summary.isBlank() ? "" : ": " + summary)
                + (state.suffix.isBlank() ? "" : " (" + state.suffix + ")"));
        tooltip.setText(title + ": " + state.label + (context.isBlank() ? "" : "\n" + context));
    }
}
