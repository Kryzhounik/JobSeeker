package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/** Confirmation edits are deliberately kept in memory. */
final class CollectionSettingsDialog extends Dialog<CollectionSettings> {
    CollectionSettingsDialog(Window owner, String operation, CollectionSettings settings) {
        if (owner != null) initOwner(owner);
        setTitle(operation + " settings");
        setHeaderText("Confirm settings for this run. Changes will not be saved.");
        setResizable(true);
        var editor = new CollectionSettingsPane(settings);
        var error = new Label();
        error.setWrapText(true);
        var scroll = new ScrollPane(editor);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(720);
        scroll.setPrefViewportHeight(570);
        scroll.setMinHeight(450);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getDialogPane().setContent(new VBox(8, scroll, error));
        getDialogPane().setPrefSize(780, 720);
        getDialogPane().setMinSize(620, 560);
        var confirm = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        getDialogPane().lookupButton(confirm).addEventFilter(ActionEvent.ACTION, event -> {
            try { editor.settings(); }
            catch (IllegalArgumentException ex) { error.setText(ex.getMessage()); event.consume(); }
        });
        setResultConverter(button -> button == confirm ? editor.settings() : null);
    }
}
