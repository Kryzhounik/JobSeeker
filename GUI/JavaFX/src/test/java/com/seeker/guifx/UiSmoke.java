package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;

public final class UiSmoke {
    private static final Path SETTINGS = Path.of(System.getProperty("user.dir"),
            "GUI/JavaFX/target/ui-smoke-settings.json");

    public static void main(String[] args) throws Exception {
        System.setProperty("seeker.gui.settings.path", SETTINGS.toString());
        Platform.startup(() -> { });
        MainController controller = null;
        try {
            FXMLLoader loader = new FXMLLoader(UiSmoke.class.getResource(
                    "/com/seeker/guifx/main-view.fxml"));
            Stage stage = onFx(() -> {
                Stage window = new Stage();
                Scene scene = new Scene(loader.load(), 1440, 900);
                scene.getStylesheets().add(UiSmoke.class.getResource(
                        "/com/seeker/guifx/application.css").toExternalForm());
                window.setTitle("Seeker Jobs UI check");
                window.setScene(scene);
                ((MainController) loader.getController()).attachStage(window);
                window.show();
                return window;
            });
            controller = loader.getController();
            TableView<?> jobs = lookup(stage, "#jobsTable", TableView.class);
            Label status = lookup(stage, "#statusLabel", Label.class);
            waitUntil(() -> status.getText().endsWith(" vacancies") && !jobs.getItems().isEmpty(),
                    "Vacancies did not load");
            int initialCount = onFx(() -> jobs.getItems().size());
            if (jobs.getColumns().size() != 16) {
                throw new AssertionError("Unexpected main-table column count");
            }
            TextField id = lookup(stage, "#idField", TextField.class);
            try {
                waitUntil(() -> !id.getText().isBlank(),
                        "Vacancy detail did not load");
            } catch (AssertionError error) {
                throw new AssertionError(onFx(() -> error.getMessage()
                        + "; status=" + status.getText()
                        + "; selected=" + jobs.getSelectionModel().getSelectedIndex()
                        + "; selectedItem=" + jobs.getSelectionModel().getSelectedItem()
                        + "; firstItem=" + jobs.getItems().getFirst()
                        + "; id=" + id.getText()), error);
            }
            String selectedId = onFx(id::getText);
            TextField idFilter = lookup(stage, "#idFilterField", TextField.class);
            Button search = lookup(stage, "#searchButton", Button.class);
            onFx(() -> { idFilter.setText(selectedId); search.fire(); return null; });
            try {
                waitUntil(() -> jobs.getItems().size() < initialCount
                        && jobs.getItems().stream().anyMatch(row ->
                                ((JobRecord) row).jobId() == Integer.parseInt(selectedId))
                        && status.getText().endsWith(" vacancies"),
                        "ID search did not include the selected vacancy");
            } catch (AssertionError error) {
                throw new AssertionError(onFx(() -> error.getMessage()
                        + "; query=" + idFilter.getText()
                        + "; count=" + jobs.getItems().size()
                        + "; status=" + status.getText()), error);
            }
            Button clear = lookup(stage, "#clearButton", Button.class);
            onFx(() -> { clear.fire(); return null; });
            waitUntil(() -> jobs.getItems().size() == initialCount && idFilter.getText().isEmpty(),
                    "Clear did not restore the table");

            checkWindow(stage, "#companiesButton", "Companies");
            checkWindow(stage, "#applicationsButton", "Applications");
            checkWindow(stage, "#collectedButton", "Collected jobs");
            checkWindow(stage, "#configButton", "Config");

            onFx(() -> {
                WritableImage image = stage.getScene().snapshot(null);
                PixelReader pixels = image.getPixelReader();
                BufferedImage bitmap = new BufferedImage((int) image.getWidth(),
                        (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
                for (int y = 0; y < bitmap.getHeight(); y++) {
                    for (int x = 0; x < bitmap.getWidth(); x++) {
                        bitmap.setRGB(x, y, pixels.getArgb(x, y));
                    }
                }
                ImageIO.write(bitmap, "png", Path.of("GUI/JavaFX/target/ui-smoke.png").toFile());
                return null;
            });
            System.out.println("UI check OK: " + initialCount + " vacancies; auxiliary windows opened");
        } finally {
            MainController finalController = controller;
            onFx(() -> {
                if (finalController != null) finalController.shutdown();
                List.copyOf(Window.getWindows()).forEach(Window::hide);
                return null;
            });
            Platform.exit();
            Files.deleteIfExists(SETTINGS);
        }
    }

    private static void checkWindow(Stage main, String buttonId, String title) throws Exception {
        Button button = lookup(main, buttonId, Button.class);
        onFx(() -> { button.fire(); return null; });
        waitUntil(() -> Window.getWindows().stream().anyMatch(window ->
                window instanceof Stage stage && stage.isShowing() && title.equals(stage.getTitle())),
                title + " window did not open");
    }

    private static <T> T lookup(Stage stage, String selector, Class<T> type) throws Exception {
        return onFx(() -> type.cast(stage.getScene().lookup(selector)));
    }

    private static void waitUntil(BooleanSupplier condition, String error) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            if (onFx(condition::getAsBoolean)) return;
            Thread.sleep(250);
        }
        throw new AssertionError(error);
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get();
    }
}
