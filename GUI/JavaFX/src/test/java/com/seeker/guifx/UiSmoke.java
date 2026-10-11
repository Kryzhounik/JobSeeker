package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
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
            @SuppressWarnings("unchecked")
            TableView<JobRecord> jobs = lookup(stage, "#jobsTable", TableView.class);
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

            onFx(() -> {
                jobs.applyCss();
                jobs.layout();
                boolean companyLink = jobs.lookupAll(".table-cell").stream()
                        .filter(node -> node instanceof TableCell<?, ?>)
                        .map(node -> (TableCell<?, ?>) node)
                        .anyMatch(cell -> cell.getTableColumn() == jobs.getColumns().get(7)
                                && cell.getGraphic() instanceof TextField field
                                && field.getOnMouseClicked() != null);
                if (!companyLink) throw new AssertionError("Company link cells are missing");

                @SuppressWarnings("unchecked")
                TableColumn<JobRecord, String> score =
                        (TableColumn<JobRecord, String>) jobs.getColumns().getFirst();
                jobs.getSortOrder().setAll(score);
                score.setSortType(TableColumn.SortType.ASCENDING);
                jobs.sort();
                int previous = Integer.MIN_VALUE;
                for (Object item : jobs.getItems()) {
                    int current = Integer.parseInt(((JobRecord) item).score());
                    if (current < previous) throw new AssertionError("Score sort is not numeric");
                    previous = current;
                }
                return null;
            });

            ToggleButton textToggle = lookup(stage, "#textToggle", ToggleButton.class);
            TextArea fullText = lookup(stage, "#fullTextArea", TextArea.class);
            onFx(() -> {
                textToggle.fire();
                if (!fullText.isVisible()) throw new AssertionError("Text view did not open");
                textToggle.fire();
                if (fullText.isVisible()) throw new AssertionError("Skills view did not return");
                return null;
            });

            checkWindow(stage, "#companiesButton", "Companies");
            checkWindow(stage, "#applicationsButton", "Applications");
            checkWindow(stage, "#collectedButton", "Collected jobs");
            checkWindow(stage, "#configButton", "Config");

            onFx(() -> {
                saveSnapshot(stage, Path.of("GUI/JavaFX/target/ui-smoke.png"));
                stage.setWidth(1100);
                stage.setHeight(750);
                return null;
            });
            Thread.sleep(300);
            onFx(() -> {
                ScrollPane filters = (ScrollPane) stage.getScene().lookup(".filter-scroll");
                if (filters.getContent().getLayoutBounds().getWidth()
                        <= filters.getViewportBounds().getWidth()) {
                    throw new AssertionError("Narrow filter bar is not scrollable");
                }
                if (idFilter.getWidth() < 110) {
                    throw new AssertionError("ID filter was squeezed on resize");
                }
                saveSnapshot(stage, Path.of("GUI/JavaFX/target/ui-smoke-small.png"));
                return null;
            });
            JobRecord applied = onFx(() -> jobs.getItems().stream()
                    .filter(job -> "Applied".equals(job.status())).findFirst().orElseThrow());
            HBox statusFilters = lookup(stage, "#statusFilters", HBox.class);
            onFx(() -> {
                statusFilters.getChildren().stream()
                        .filter(node -> node instanceof CheckBox)
                        .map(node -> (CheckBox) node)
                        .forEach(check -> check.setSelected("New".equals(check.getText())));
                search.fire();
                return null;
            });
            waitUntil(() -> status.getText().endsWith(" vacancies")
                    && jobs.getItems().stream().allMatch(job -> "New".equals(job.status()))
                    && jobs.getItems().stream().noneMatch(job -> job.jobId() == applied.jobId()),
                    "New-only filter did not hide the Applied vacancy");
            MainController ui = controller;
            onFx(() -> { ui.focusJobFromAuxiliary(applied.jobId()); return null; });
            waitUntil(() -> status.getText().endsWith(" vacancies")
                    && jobs.getSelectionModel().getSelectedItem() != null
                    && jobs.getSelectionModel().getSelectedItem().jobId() == applied.jobId()
                    && Integer.toString(applied.jobId()).equals(idFilter.getText()),
                    "Applications navigation did not reveal and select the vacancy");
            onFx(() -> {
                boolean appliedEnabled = statusFilters.getChildren().stream()
                        .filter(node -> node instanceof CheckBox)
                        .map(node -> (CheckBox) node)
                        .anyMatch(check -> "Applied".equals(check.getText()) && check.isSelected());
                if (!appliedEnabled) throw new AssertionError("Applied filter remained disabled");
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
        onFx(() -> {
            Stage auxiliary = Window.getWindows().stream()
                    .filter(window -> window instanceof Stage stage
                            && stage.isShowing() && title.equals(stage.getTitle()))
                    .map(window -> (Stage) window).findFirst().orElseThrow();
            if (auxiliary.getOwner() != null) {
                throw new AssertionError(title + " is still owned by the main window");
            }
            return null;
        });
    }

    private static void saveSnapshot(Stage stage, Path destination) throws Exception {
        WritableImage image = stage.getScene().snapshot(null);
        PixelReader pixels = image.getPixelReader();
        BufferedImage bitmap = new BufferedImage((int) image.getWidth(),
                (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < bitmap.getHeight(); y++) {
            for (int x = 0; x < bitmap.getWidth(); x++) {
                bitmap.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        int background = bitmap.getRGB(bitmap.getWidth() - 5, bitmap.getHeight() - 5);
        int occupied = 0;
        int sampled = 0;
        for (int y = 0; y < bitmap.getHeight(); y += 8) {
            for (int x = 0; x < bitmap.getWidth(); x += 8) {
                sampled++;
                if (bitmap.getRGB(x, y) != background) occupied++;
            }
        }
        if (occupied < sampled / 10) {
            throw new AssertionError("UI snapshot appears blank: " + destination);
        }
        ImageIO.write(bitmap, "png", destination.toFile());
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
