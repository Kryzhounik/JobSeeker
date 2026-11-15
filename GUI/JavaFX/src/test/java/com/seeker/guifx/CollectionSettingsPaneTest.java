package com.seeker.guifx;

import com.seeker.collector.common.CollectionSettings;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class CollectionSettingsPaneTest {
    private static CollectionSettings defaults() {
        return new CollectionSettings(List.of("justjoin", "linkedin"),
                new CollectionSettings.LinkedIn(List.of("Java"), List.of("Moldova:106178099", "Ukraine:102264497"),
                        List.of("senior", "entry_level", "manager"), List.of("remote"),
                        List.of("remote", "hybrid"), List.of("full_time", "contract"), "week", "", 10),
                new CollectionSettings.JustJoin("java", "all-locations", "14", List.of("en", "ru"),
                        List.of("mid", "senior"), "DESC", "published", 0));
    }

    @Test void sharedEditorValidationConfirmationAndLayout() throws Exception {
        Platform.startup(() -> { });
        try {
            FutureTask<Void> checks = new FutureTask<>(() -> {
                var saved = defaults();
                var single = new CollectionSettings(List.of("linkedin"), saved.linkedin(), null);
                var singleEditor = new CollectionSettingsPane(single);
                new Scene(singleEditor);
                singleEditor.applyCss(); singleEditor.layout();
                assertEquals(single, singleEditor.settings());
                assertTrue(((CheckBox) singleEditor.lookup("#source-justjoin")).isDisabled());
                var editor = new CollectionSettingsPane(saved);
                new Scene(editor, 780, 750);
                editor.applyCss(); editor.layout();
                assertEquals(saved, editor.settings());
                field(editor, "li-keywords").setText("Java, AI");
                assertEquals(List.of("Java", "AI"), editor.settings().linkedin().keywords());
                field(editor, "li-limit").setText("0");
                assertThrows(IllegalArgumentException.class, editor::settings);
                field(editor, "li-limit").setText("oops");
                assertTrue(assertThrows(IllegalArgumentException.class, editor::settings).getMessage().contains("integer"));
                field(editor, "li-limit").setText("25");
                field(editor, "jj-limit").setText("-1");
                assertThrows(IllegalArgumentException.class, editor::settings);
                field(editor, "jj-limit").setText("0");
                assertEquals(25, editor.settings().linkedin().limit());
                assertEquals(0, editor.settings().justjoin().limit());
                assertEquals(saved.sources(), editor.settings().sources());
                ((CheckBox) editor.lookup("#source-linkedin")).setSelected(false);
                field(editor, "li-limit").setText("invalid disabled value");
                assertEquals(List.of("justjoin"), editor.settings().sources());
                ((CheckBox) editor.lookup("#source-justjoin")).setSelected(false);
                assertThrows(IllegalArgumentException.class, editor::settings);

                var dialog = new CollectionSettingsDialog(null, "Collect", saved);
                var dialogPane = dialog.getDialogPane();
                dialogPane.applyCss(); dialogPane.layout();
                assertEquals(2, dialogPane.getButtonTypes().size());
                field(dialogPane, "li-limit").setText("0");
                ButtonType confirm = dialogPane.getButtonTypes().getFirst();
                ((Button) dialogPane.lookupButton(confirm)).fire();
                assertNull(dialog.getResult());
                field(dialogPane, "li-limit").setText("32");
                ((Button) dialogPane.lookupButton(confirm)).fire();
                assertEquals(32, dialog.getResult().linkedin().limit());
                assertEquals(10, saved.linkedin().limit());
                var cancelled = new CollectionSettingsDialog(null, "Batch", saved);
                cancelled.getDialogPane().applyCss(); cancelled.getDialogPane().layout();
                field(cancelled.getDialogPane(), "li-limit").setText("99");
                ((Button) cancelled.getDialogPane().lookupButton(ButtonType.CANCEL)).fire();
                assertNull(cancelled.getResult());
                var reopened = new CollectionSettingsDialog(null, "Batch", saved);
                reopened.getDialogPane().applyCss(); reopened.getDialogPane().layout();
                assertEquals("10", field(reopened.getDialogPane(), "li-limit").getText());

                var windows = new AuxiliaryWindows(null, ignored -> {}, new SettingsStore(Path.of("tmp/ui-settings-test")),
                        ignored -> {}, ignored -> {});
                try {
                    var tabs = windows.configContent(List.of(), saved);
                    assertEquals(List.of("Filters", "Collection"), tabs.getTabs().stream().map(Tab::getText).toList());
                    tabs.getSelectionModel().select(1);
                    snapshot(tabs, "config-collection.png", 820, 760);
                    var preview = reopened.getDialogPane();
                    reopened.setDialogPane(new DialogPane());
                    snapshot(preview, "confirm-batch.png", 800, 750);
                } finally { windows.shutdown(); }
                return null;
            });
            Platform.runLater(checks);
            checks.get(25, TimeUnit.SECONDS);
        } finally { Platform.exit(); }
    }

    private static TextField field(Parent root, String id) {
        return (TextField) root.lookup("#" + id);
    }

    /** Render off screen: no window is shown or focused. */
    private static void snapshot(javafx.scene.layout.Region root, String name, int width, int height) throws Exception {
        if (root.getScene() != null && root.getScene().getRoot() == root)
            root.getScene().setRoot(new javafx.scene.Group());
        var canvas = new javafx.scene.layout.StackPane(root);
        new Scene(canvas, width, height);
        canvas.resize(width, height); canvas.applyCss(); canvas.layout();
        if (root instanceof DialogPane dialog) {
            assertTrue(dialog.getContent().getLayoutBounds().getHeight() > 500);
            ((javafx.scene.layout.VBox) dialog.getContent()).layout();
            assertTrue(((javafx.scene.layout.VBox) dialog.getContent()).getChildren().getFirst()
                    .getLayoutBounds().getHeight() >= 450);
        }
        var image = canvas.snapshot(null, null);
        var pixels = new java.awt.image.BufferedImage((int) image.getWidth(), (int) image.getHeight(),
                java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < pixels.getHeight(); y++) for (int x = 0; x < pixels.getWidth(); x++)
            pixels.setRGB(x, y, image.getPixelReader().getArgb(x, y));
        Path output = Path.of(System.getProperty("java.io.tmpdir")).resolve("collection-settings-ui");
        Files.createDirectories(output);
        javax.imageio.ImageIO.write(pixels, "png", output.resolve(name).toFile());
    }
}
