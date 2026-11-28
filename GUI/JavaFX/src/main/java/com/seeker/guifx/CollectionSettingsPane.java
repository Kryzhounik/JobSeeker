package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared editor only: no persistence or operation execution. */
final class CollectionSettingsPane extends VBox {
    private final CheckBox linkedin = new CheckBox();
    private final CheckBox justjoin = new CheckBox();
    private final CheckBox includeRecommendations = new CheckBox("Include recommendations");
    private final Map<String, TextInputControl> fields = new LinkedHashMap<>();
    private final CollectionSettings initial;
    private final ListView<String> queries = new ListView<>();

    CollectionSettingsPane(CollectionSettings settings) {
        super(10);
        initial = settings;
        setPadding(new Insets(12));
        getStylesheets().add(CollectionSettingsPane.class.getResource("collection-settings.css").toExternalForm());
        linkedin.setSelected(settings.sources().contains("linkedin"));
        justjoin.setSelected(settings.sources().contains("justjoin"));
        linkedin.setId("source-linkedin");
        justjoin.setId("source-justjoin");
        getChildren().add(new Label("List values are separated by commas. Empty filters mean no restriction."));
        var li = settings.linkedin();
        if (li == null) {
            linkedin.setDisable(true);
            getChildren().add(sourcePane(linkedin, "LinkedIn", new Label(
                    "LinkedIn configuration is missing or invalid. Fix its properties file to enable it."), null));
        } else {
        GridPane l = grid();
        includeRecommendations.setId("li-include-recommendations");
        includeRecommendations.setSelected(li.includeRecommendations());
        l.add(includeRecommendations, 1, l.getRowCount());
        queries.setId("li-keywords");
        queries.getItems().setAll(li.keywords());
        queries.setEditable(true);
        queries.setCellFactory(view -> new javafx.scene.control.cell.TextFieldListCell<>(
                new javafx.util.converter.DefaultStringConverter()) {
            private TextField observedEditor;
            @Override public void startEdit() {
                super.startEdit();
                if (getGraphic() instanceof TextField editor && editor != observedEditor) {
                    observedEditor = editor;
                    editor.focusedProperty().addListener((obs, old, focused) -> {
                        if (!focused && isEditing()) commitEdit(editor.getText());
                    });
                }
            }
        });
        queries.setPrefHeight(110);
        queries.setMinHeight(80);
        Button addQuery = queryButton("+", "li-query-add", "Add query");
        Button removeQuery = queryButton("\u2212", "li-query-remove", "Remove query");
        Button up = queryButton("\u2191", "li-query-up", "Move query up");
        Button down = queryButton("\u2193", "li-query-down", "Move query down");
        addQuery.setOnAction(event -> {
            queries.getItems().add("");
            int index = queries.getItems().size() - 1;
            queries.getSelectionModel().select(index);
            queries.scrollTo(index);
            queries.edit(index);
        });
        removeQuery.disableProperty().bind(queries.getSelectionModel().selectedIndexProperty().lessThan(0));
        up.disableProperty().bind(queries.getSelectionModel().selectedIndexProperty().lessThanOrEqualTo(0));
        down.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() ->
                queries.getSelectionModel().getSelectedIndex() < 0 || queries.getSelectionModel().getSelectedIndex()
                        >= queries.getItems().size() - 1,
                queries.getSelectionModel().selectedIndexProperty(), queries.getItems()));
        removeQuery.setOnAction(event -> queries.getItems().remove(queries.getSelectionModel().getSelectedIndex()));
        up.setOnAction(event -> moveQuery(-1));
        down.setOnAction(event -> moveQuery(1));
        l.addRow(l.getRowCount(), new Label("Queries (in order)"),
                new VBox(5, queries, new HBox(5, addQuery, removeQuery, up, down)));
        l.addRow(l.getRowCount(), new Label("Locations (in order)"), locationsField(join(li.locations())));
        add(l, "li.experience", "Experience", join(li.experience()), "entry_level,senior,manager");
        add(l, "li.remote", "AccountRemote workplace", join(li.remoteBroadWorkplace()), "remote,hybrid,office");
        add(l, "li.workplace", "Country workplace", join(li.workplace()), "remote,hybrid,office");
        add(l, "li.types", "Job types", join(li.jobTypes()), "full_time,part_time,contract");
        add(l, "li.date", "Posted within", li.datePosted(), "day, week, month, or empty");
        var liLimit = field("li.limit", "" + li.limit(), "Positive integer");
        l.disableProperty().bind(linkedin.selectedProperty().not());
        getChildren().add(sourcePane(linkedin, "LinkedIn", l, liLimit));
        }
        var jj = settings.justjoin();
        if (jj == null) {
            justjoin.setDisable(true);
            getChildren().add(sourcePane(justjoin, "JustJoinIT", new Label(
                    "JustJoinIT configuration is missing or invalid. Fix its properties file to enable it."), null));
        } else {
        GridPane j = grid();
        add(j, "jj.tech", "Technology category", jj.mainTech(), "java");
        add(j, "jj.location", "Location", jj.location(), "all-locations");
        add(j, "jj.days", "Published within (days)", jj.publishedDays(), "Empty: no age filter");
        add(j, "jj.languages", "Languages", join(jj.languages()), "en,ru,uk");
        add(j, "jj.experience", "Experience levels", join(jj.experienceLevels()), "junior,mid,senior");
        add(j, "jj.order", "Order", jj.orderBy(), "ASC or DESC");
        add(j, "jj.sort", "Sort by", jj.sortBy(), "published");
        var jjLimit = field("jj.limit", "" + jj.limit(), "0: unlimited");
        j.disableProperty().bind(justjoin.selectedProperty().not());
        getChildren().add(sourcePane(justjoin, "JustJoinIT", j, jjLimit));
        }
    }

    CollectionSettings settings() {
        var sources = new ArrayList<String>();
        // Preserve configured execution order when both remain enabled.
        for (String source : initial.sources()) if (enabled(source)) sources.add(source);
        for (String source : List.of("linkedin", "justjoin"))
            if (enabled(source) && !sources.contains(source)) sources.add(source);
        var li = linkedin.isSelected() ? new CollectionSettings.LinkedIn(
                queries.getItems().stream().map(String::strip).filter(value -> !value.isEmpty()).toList(),
                list("li.locations"), list("li.experience"), list("li.remote"), list("li.workplace"),
                list("li.types"), value("li.date"), integer("li.limit", "LinkedIn limit"),
                includeRecommendations.isSelected())
                : initial.linkedin();
        var jj = justjoin.isSelected() ? new CollectionSettings.JustJoin(value("jj.tech"), value("jj.location"),
                value("jj.days"), list("jj.languages"), list("jj.experience"), value("jj.order"),
                value("jj.sort"), integer("jj.limit", "JustJoinIT limit")) : initial.justjoin();
        return new CollectionSettings(sources, li, jj);
    }

    private static StackPane sourcePane(CheckBox source, String title, Node content, TextField limit) {
        var pane = new TitledPane(title, content);
        pane.getStyleClass().add("collection-source-pane");
        source.setAccessibleText(title);
        source.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        // Keep the toggle outside TitledPane's click target, but visually in its header.
        var section = new StackPane(pane, source);
        StackPane.setAlignment(source, Pos.TOP_LEFT);
        StackPane.setMargin(source, new Insets(5, 0, 0, 8));
        if (limit == null) return section;
        limit.setPrefColumnCount(4);
        limit.setAccessibleText(title + " accepted limit");
        limit.disableProperty().bind(source.selectedProperty().not());
        var label = new Label("Limit:");
        label.setLabelFor(limit);
        var limitControl = new HBox(6, label, limit);
        limitControl.setAlignment(Pos.CENTER_LEFT);
        limitControl.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        limitControl.getStyleClass().add("collection-source-limit");
        section.getChildren().add(limitControl);
        StackPane.setAlignment(limitControl, Pos.TOP_RIGHT);
        StackPane.setMargin(limitControl, new Insets(3, 8, 0, 0));
        return section;
    }

    private boolean enabled(String source) { return (source.equals("linkedin") ? linkedin : justjoin).isSelected(); }
    private static Button queryButton(String symbol, String id, String description) {
        Button button = new Button(symbol);
        button.setId(id);
        button.setTooltip(new Tooltip(description));
        button.setAccessibleText(description);
        return button;
    }
    private void moveQuery(int direction) {
        int index = queries.getSelectionModel().getSelectedIndex();
        int target = index + direction;
        String query = queries.getItems().remove(index);
        queries.getItems().add(target, query);
        queries.getSelectionModel().select(target);
    }
    private String value(String key) { return fields.get(key).getText().strip(); }
    private List<String> list(String key) { return PropertySettings.csv(value(key)); }
    private int integer(String key, String label) {
        try { return Integer.parseInt(value(key)); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + " must be an integer"); }
    }
    private static String join(List<String> values) { return String.join(",", values); }
    private static GridPane grid() {
        var grid = new GridPane();
        grid.setHgap(10); grid.setVgap(8); grid.setPadding(new Insets(8));
        var values = new ColumnConstraints(); values.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(new ColumnConstraints(180), values);
        return grid;
    }
    private void add(GridPane grid, String key, String label, String value, String hint) {
        grid.addRow(grid.getRowCount(), new Label(label), field(key, value, hint));
    }
    private TextArea locationsField(String value) {
        var field = new TextArea(value) {
            private final javafx.scene.text.Text measure = new javafx.scene.text.Text();

            @Override public javafx.geometry.Orientation getContentBias() {
                return javafx.geometry.Orientation.HORIZONTAL;
            }

            @Override protected double computePrefHeight(double width) {
                Region content = (Region) lookup(".content");
                Insets padding = content == null ? Insets.EMPTY : content.getInsets();
                Insets border = getInsets();
                measure.setFont(getFont());
                measure.setText(getText().isEmpty() ? " " : getText());
                measure.setWrappingWidth(width <= 0 ? 0 : Math.max(1, width
                        - border.getLeft() - border.getRight() - padding.getLeft() - padding.getRight()));
                return Math.ceil(measure.getLayoutBounds().getHeight())
                        + border.getTop() + border.getBottom() + padding.getTop() + padding.getBottom();
            }
        };
        field.setId("li-locations");
        field.setWrapText(true);
        field.setPrefColumnCount(1);
        field.setMinWidth(0);
        field.setMinHeight(Region.USE_PREF_SIZE);
        field.setMaxHeight(Region.USE_PREF_SIZE);
        field.setTooltip(new Tooltip("Moldova:106178099,Ukraine:102264497; AccountRemote requires a country seed"));
        field.textProperty().addListener(observable -> field.requestLayout());
        field.fontProperty().addListener(observable -> field.requestLayout());
        fields.put("li.locations", field);
        return field;
    }
    private TextField field(String key, String value, String hint) {
        var field = new TextField(value);
        field.setId(key.replace('.', '-')); field.setPromptText(hint); field.setTooltip(new Tooltip(hint));
        fields.put(key, field);
        return field;
    }
}
