package com.seeker.guifx;

import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Cursor;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

final class WindowSupport {
    static <T> T loadView(Object controller, String resource) {
        FXMLLoader loader = new FXMLLoader(WindowSupport.class.getResource(resource));
        loader.setController(controller);
        try { return loader.load(); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "javafx-auxiliary-data");
        thread.setDaemon(true);
        return thread;
    });
    private final SettingsStore settingsStore;

    WindowSupport(SettingsStore settingsStore) { this.settingsStore = settingsStore; }

    void shutdown() { ioExecutor.shutdownNow(); }

    <T> void submit(Callable<T> work, Consumer<T> success, Consumer<Exception> failure) {
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(event -> {
            try { success.accept(task.getValue()); }
            catch (RuntimeException error) { failure.accept(error); }
        });
        task.setOnFailed(event -> {
            Throwable error = task.getException();
            failure.accept(error instanceof Exception exception
                    ? exception : new RuntimeException(error));
        });
        ioExecutor.execute(task);
    }

    Stage stage(String title, double width, double height) {
        Stage stage = new Stage();
        stage.sceneProperty().addListener((ignored, previous, scene) -> {
            if (scene != null) scene.getStylesheets().add(WindowSupport.class.getResource(
                    "/com/seeker/guifx/application.css").toExternalForm());
        });
        stage.initModality(Modality.NONE);
        stage.setTitle(title);
        String key = title.equals("Collected jobs") ? "collected"
                : title.toLowerCase(java.util.Locale.ROOT);
        stage.setWidth(settingsStore.windowWidth(key, width));
        stage.setHeight(settingsStore.windowHeight(key, height));
        stage.getIcons().add(new Image(WindowSupport.class.getResourceAsStream(
                "/com/seeker/guifx/SeekerJobsIcon.png")));
        stage.setOnHiding(event -> {
            try { settingsStore.saveWindowSize(key, stage.getWidth(), stage.getHeight()); }
            catch (java.io.IOException error) { showError("Settings save failed", error); }
        });
        return stage;
    }

    <T> void restoreTableSort(TableView<T> table, String key) {
        table.setSortPolicy(view -> {
            boolean sorted = TableView.DEFAULT_SORT_POLICY.call(view);
            TableColumn<T, ?> column = view.getSortOrder().isEmpty()
                    ? null : view.getSortOrder().getFirst();
            try {
                settingsStore.saveTableSort(key, column == null ? "" : column.getText(),
                        column != null && column.getSortType() == TableColumn.SortType.DESCENDING);
            } catch (java.io.IOException error) {
                showError("Settings save failed", error);
            }
            return sorted;
        });
        String savedColumn = settingsStore.tableSortColumn(key);
        table.getColumns().stream().filter(column -> column.getText().equals(savedColumn))
                .findFirst().ifPresent(column -> {
                    column.setSortType(settingsStore.tableSortDescending(key)
                            ? TableColumn.SortType.DESCENDING : TableColumn.SortType.ASCENDING);
                    table.getSortOrder().setAll(column);
                    table.sort();
                });
    }

    static java.time.LocalDate parseDisplayDate(String value) {
        try {
            return java.time.LocalDate.parse(value,
                    java.time.format.DateTimeFormatter.ofPattern("dd.MM.uuuu"));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    static String displayDate(String value) {
        try { return java.time.LocalDate.parse(value.substring(0, 10)).format(
                java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy")); }
        catch (RuntimeException ignored) { return value; }
    }

    static void openUrl(String value) {
        try { if (Desktop.isDesktopSupported() && !value.isBlank()) browse(value); }
        catch (Exception error) { showError("Could not open link", error); }
    }

    static void browse(String value) throws java.io.IOException {
        Desktop.getDesktop().browse(URI.create(value));
    }

    static void showError(String title, Exception error) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.ERROR, error.getMessage());
        alert.setTitle(title);
        alert.show();
    }

    static class HyperlinkCell<T> extends TableCell<T, String> {
        private final java.util.function.Function<T, String> text;
        private final Consumer<T> action;
        private final TextField linkText = new TextField();
        HyperlinkCell(java.util.function.Function<T, String> text, Consumer<T> action) {
            this.text = text; this.action = action;
            linkText.setEditable(false);
            linkText.setFocusTraversable(true);
            linkText.setCursor(Cursor.HAND);
            linkText.getStyleClass().add("filter-link-cell");
            CellTextFieldWidth.fitToText(linkText, this);
            linkText.setOnMouseClicked(event -> {
                if (event.getClickCount() == 1 && linkText.getSelectedText().isEmpty()) {
                    T row = getTableRow() == null ? null : getTableRow().getItem();
                    if (row != null) action.accept(row);
                }
            });
        }
        @Override protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            T row = empty || getTableRow() == null ? null : getTableRow().getItem();
            if (row == null) { setGraphic(null); return; }
            linkText.setText(text.apply(row));
            setGraphic(linkText);
        }
    }
}
