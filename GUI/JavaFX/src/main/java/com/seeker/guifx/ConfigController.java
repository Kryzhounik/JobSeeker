package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import com.seeker.collector.core.CollectionSettingsLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

final class ConfigController {
    private final ClientData data;
    private final WindowSupport windows;
    private final Path collectionSettingsRoot;
    @javafx.fxml.FXML private VBox filterRows;
    @javafx.fxml.FXML private BorderPane collectionPane;
    @javafx.fxml.FXML private Button saveButton;
    @javafx.fxml.FXML private Label saveStatus;

    ConfigController(ClientData data, WindowSupport windows, Path collectionSettingsRoot) {
        this.data = data;
        this.windows = windows;
        this.collectionSettingsRoot = collectionSettingsRoot;
    }

    void showConfig() {
        windows.submit(() -> new ConfigData(data.loadConfigRows(), CollectionSettingsLoader.loadForEditing(collectionSettingsRoot)),
                this::buildConfig, error -> WindowSupport.showError("Config load failed", error));
    }

    private void buildConfig(ConfigData config) {
        Stage stage = windows.stage("Config", 900, 1000);
        stage.setScene(new Scene(configContent(config.rows, config.settings), 900, 1000));
        stage.setWidth(Math.max(stage.getWidth(), 900));
        stage.setHeight(Math.max(stage.getHeight(), 1000));
        stage.show();
    }

    TabPane configContent(List<ConfigEntryDto> rows, CollectionSettings settings) {
        TabPane tabs = WindowSupport.loadView(this, "config-view.fxml");
        VBox content = filterRows;
        BorderPane collection = collectionPane;
        for (ConfigEntryDto row : rows) {
            int key = row.key();
            CheckBox check = new CheckBox(row.configName());
            check.setSelected("1".equals(row.value()));
            check.setOnAction(event -> {
                boolean enabled = check.isSelected();
                check.setDisable(true);
                windows.submit(() -> {
                    data.setConfigValue(key, enabled);
                    return null;
                }, ignored -> check.setDisable(false), error -> {
                    check.setSelected(!enabled);
                    check.setDisable(false);
                    WindowSupport.showError("Config update failed", error);
                });
            });
            content.getChildren().add(check);
        }
        var editor = new CollectionSettingsPane(settings);
        Button save = saveButton;
        Label status = saveStatus;
        save.setOnAction(event -> {
            try {
                CollectionSettings edited = editor.settings();
                editor.setDisable(true);
                save.setDisable(true);
                windows.submit(() -> {
                    CollectionSettingsLoader.save(collectionSettingsRoot, edited);
                    return null;
                }, ignored -> {
                    status.setText("Saved"); editor.setDisable(false); save.setDisable(false);
                }, error -> {
                    status.setText(error.getMessage()); editor.setDisable(false); save.setDisable(false);
                });
            } catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
        });
        var scroll = new javafx.scene.control.ScrollPane(editor);
        scroll.setFitToWidth(true);
        collection.setCenter(scroll);
        return tabs;
    }

    private record ConfigData(List<ConfigEntryDto> rows, CollectionSettings settings) { }
}
