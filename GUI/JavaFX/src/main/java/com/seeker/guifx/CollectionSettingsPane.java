package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.common.PropertySettings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Shared editor only: no persistence or operation execution. */
final class CollectionSettingsPane extends VBox {
    private final CheckBox linkedin = new CheckBox("LinkedIn");
    private final CheckBox justjoin = new CheckBox("JustJoinIT");
    private final Map<String, TextField> fields = new LinkedHashMap<>();
    private final CollectionSettings initial;

    CollectionSettingsPane(CollectionSettings settings) {
        super(10);
        initial = settings;
        setPadding(new Insets(12));
        linkedin.setSelected(settings.sources().contains("linkedin"));
        justjoin.setSelected(settings.sources().contains("justjoin"));
        linkedin.setId("source-linkedin");
        justjoin.setId("source-justjoin");
        getChildren().addAll(new Label("Sources"), new HBox(16, linkedin, justjoin),
                new Label("List values are separated by commas. Empty filters mean no restriction."));
        var li = settings.linkedin();
        if (li == null) {
            linkedin.setDisable(true);
            getChildren().add(new Label("LinkedIn configuration is missing or invalid. Fix its properties file to enable it."));
        } else {
        GridPane l = grid();
        add(l, "li.keywords", "Keyword", li.keywords(), "One search query");
        add(l, "li.locations", "Locations (in order)", join(li.locations()), "Moldova:106178099,Ukraine:102264497; AccountRemote requires a country seed");
        add(l, "li.experience", "Experience", join(li.experience()), "entry_level,senior,manager");
        add(l, "li.remote", "AccountRemote workplace", join(li.remoteBroadWorkplace()), "remote,hybrid,office");
        add(l, "li.workplace", "Country workplace", join(li.workplace()), "remote,hybrid,office");
        add(l, "li.types", "Job types", join(li.jobTypes()), "full_time,part_time,contract");
        add(l, "li.date", "Posted within", li.datePosted(), "day, week, month, or empty");
        add(l, "li.limit", "Accepted limit", "" + li.limit(), "Positive integer");
        l.disableProperty().bind(linkedin.selectedProperty().not());
        getChildren().add(new TitledPane("LinkedIn", l));
        }
        var jj = settings.justjoin();
        if (jj == null) {
            justjoin.setDisable(true);
            getChildren().add(new Label("JustJoinIT configuration is missing or invalid. Fix its properties file to enable it."));
        } else {
        GridPane j = grid();
        add(j, "jj.tech", "Technology category", jj.mainTech(), "java");
        add(j, "jj.location", "Location", jj.location(), "all-locations");
        add(j, "jj.days", "Published within (days)", jj.publishedDays(), "Empty: no age filter");
        add(j, "jj.languages", "Languages", join(jj.languages()), "en,ru,uk");
        add(j, "jj.experience", "Experience levels", join(jj.experienceLevels()), "junior,mid,senior");
        add(j, "jj.order", "Order", jj.orderBy(), "ASC or DESC");
        add(j, "jj.sort", "Sort by", jj.sortBy(), "published");
        add(j, "jj.limit", "Accepted limit", "" + jj.limit(), "0: unlimited");
        j.disableProperty().bind(justjoin.selectedProperty().not());
        getChildren().add(new TitledPane("JustJoinIT", j));
        }
    }

    CollectionSettings settings() {
        var sources = new ArrayList<String>();
        // Preserve configured execution order when both remain enabled.
        for (String source : initial.sources()) if (enabled(source)) sources.add(source);
        for (String source : List.of("linkedin", "justjoin"))
            if (enabled(source) && !sources.contains(source)) sources.add(source);
        var li = linkedin.isSelected() ? new CollectionSettings.LinkedIn(value("li.keywords"),
                list("li.locations"), list("li.experience"), list("li.remote"), list("li.workplace"),
                list("li.types"), value("li.date"), initial.linkedin().sort(), integer("li.limit", "LinkedIn limit"))
                : initial.linkedin();
        var jj = justjoin.isSelected() ? new CollectionSettings.JustJoin(value("jj.tech"), value("jj.location"),
                value("jj.days"), list("jj.languages"), list("jj.experience"), value("jj.order"),
                value("jj.sort"), integer("jj.limit", "JustJoinIT limit")) : initial.justjoin();
        return new CollectionSettings(sources, li, jj);
    }

    private boolean enabled(String source) { return (source.equals("linkedin") ? linkedin : justjoin).isSelected(); }
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
        var field = new TextField(value);
        field.setId(key.replace('.', '-')); field.setPromptText(hint); field.setTooltip(new Tooltip(hint));
        fields.put(key, field);
        grid.addRow(grid.getRowCount(), new Label(label), field);
    }
}
