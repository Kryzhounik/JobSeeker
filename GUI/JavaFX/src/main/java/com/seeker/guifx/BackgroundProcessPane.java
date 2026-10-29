package com.seeker.guifx;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
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
    private final Map<String, TextField> extraFields = new LinkedHashMap<>();
    private final GridPane extraGrid = new GridPane();
    private final TextArea details = new TextArea();
    private final ScrollPane scroll = new ScrollPane();
    private State state = State.RUNNING;
    private boolean finished;
    private String summary = "";
    private String context = "";
    private boolean resizingWidth;
    private boolean resizingHeight;
    private double dragX, dragY, dragWidth, dragHeight;

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
        extraGrid.setHgap(10);
        extraGrid.setVgap(6);
        ColumnConstraints extraValues = new ColumnConstraints();
        extraValues.setHgrow(Priority.ALWAYS);
        extraGrid.getColumnConstraints().addAll(new ColumnConstraints(155), extraValues);
        extraGrid.setVisible(false);
        extraGrid.setManaged(false);
        VBox content = new VBox(8, grid, extraGrid, new Label("Details"), details);
        content.setPadding(new Insets(12));
        VBox.setVgrow(details, Priority.ALWAYS);
        scroll.setContent(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
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
        panel.setPrefWidth(570);
        panel.setMinSize(0, 0);
        panel.setMaxWidth(Region.USE_PREF_SIZE);
        panel.setMaxHeight(Region.USE_PREF_SIZE);
        panel.setPadding(new Insets(0, 0, 6, 6));
        installResize(panel, area);
        panel.visibleProperty().bind(dock.selectedProperty());
        panel.managedProperty().bind(panel.visibleProperty());
        VBox.setVgrow(scroll, Priority.ALWAYS);
        StackPane.setAlignment(panel, Pos.TOP_RIGHT);
        StackPane.setMargin(panel, new Insets(8));
        area.overlay().getChildren().add(panel);
        setProcessState(State.RUNNING);
    }

    private void installResize(VBox panel, Area area) {
        panel.addEventFilter(MouseEvent.MOUSE_MOVED, event -> {
            var point = panel.sceneToLocal(event.getSceneX(), event.getSceneY());
            boolean left = point.getX() <= 6;
            boolean bottom = point.getY() >= panel.getHeight() - 6;
            panel.setCursor(left && bottom ? Cursor.SW_RESIZE
                    : left ? Cursor.W_RESIZE : bottom ? Cursor.S_RESIZE : Cursor.DEFAULT);
        });
        panel.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
            if (!resizingWidth && !resizingHeight) panel.setCursor(Cursor.DEFAULT);
        });
        panel.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;
            var point = panel.sceneToLocal(event.getSceneX(), event.getSceneY());
            resizingWidth = point.getX() <= 6;
            resizingHeight = point.getY() >= panel.getHeight() - 6;
            if (!resizingWidth && !resizingHeight) return;
            dragX = event.getSceneX();
            dragY = event.getSceneY();
            dragWidth = panel.getWidth();
            dragHeight = panel.getHeight();
            event.consume();
        });
        panel.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!resizingWidth && !resizingHeight) return;
            if (resizingWidth) {
                panel.setPrefWidth(Math.min(Math.max(0, area.overlay().getWidth() - 16),
                        Math.max(340, dragWidth + dragX - event.getSceneX())));
            }
            if (resizingHeight) {
                panel.setPrefHeight(Math.min(Math.max(0, area.overlay().getHeight() - 16),
                        Math.max(180, dragHeight + event.getSceneY() - dragY)));
            }
            event.consume();
        });
        panel.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (!resizingWidth && !resizingHeight) return;
            resizingWidth = false;
            resizingHeight = false;
            panel.setCursor(Cursor.DEFAULT);
            event.consume();
        });
    }

    protected final void beginProcess() {
        finished = false;
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
        dismissDock.setVisible(finished);
        dismissDock.setManaged(finished);
        dismissPanel.setVisible(finished);
        dismissPanel.setManaged(finished);
        refreshDock();
    }

    protected final void finishProcess(State state) {
        finished = true;
        setProcessState(state);
    }

    private void dismiss() {
        if (!finished) return;
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

    protected final void replaceExtraFields(Map<String, String> values) {
        if (!java.util.List.copyOf(extraFields.keySet()).equals(java.util.List.copyOf(values.keySet()))) {
            extraGrid.getChildren().clear();
            extraFields.clear();
            values.forEach((name, text) -> {
                TextField value = new TextField();
                value.setEditable(false);
                value.setMaxWidth(Double.MAX_VALUE);
                Label label = new Label(name);
                label.setWrapText(true);
                label.setMinWidth(0);
                label.setMaxWidth(155);
                extraGrid.addRow(extraFields.size(), label, value);
                extraFields.put(name, value);
            });
        }
        values.forEach((name, text) -> extraFields.get(name).setText(text));
        extraGrid.setVisible(!values.isEmpty());
        extraGrid.setManaged(!values.isEmpty());
    }

    protected final void setDetails(String text) {
        details.setText(text);
    }

    protected final void setContent(Node content) {
        scroll.setContent(content);
    }

    private void refreshDock() {
        dock.setText(title + (summary.isBlank() ? "" : ": " + summary)
                + (state.suffix.isBlank() ? "" : " (" + state.suffix + ")"));
        tooltip.setText(title + ": " + state.label + (context.isBlank() ? "" : "\n" + context));
    }
}
