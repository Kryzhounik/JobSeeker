package com.seeker.guifx;

import com.seeker.api.DriverApi;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class DataViewTestSupport implements AutoCloseable {
    private final FXMLLoader loader = new FXMLLoader(getClass().getResource("main-view.fxml"));
    private final DriverApi api = new DriverApi(ClientData.findProjectRoot());
    private MainController controller;
    private Stage stage;

    private DataViewTestSupport() throws Exception { }

    static DataViewTestSupport open(Path database, Path settings) throws Exception {
        System.setProperty("seeker.gui.settings.path", settings.toString());
        Platform.startup(() -> { });
        DataViewTestSupport view = new DataViewTestSupport();
        onFx(() -> {
            Parent root = view.loader.load();
            view.stage = new Stage();
            view.stage.setScene(new Scene(root, 1440, 900));
            root.applyCss();
            root.layout();
            view.controller = view.loader.getController();
            view.controller.attachStage(view.stage);
            view.controller.initializeData(view.api, database);
            return null;
        });
        view.awaitLoaded();
        return view;
    }

    @SuppressWarnings("unchecked")
    <T> T get(String id) { return (T) loader.getNamespace().get(id); }
    MainController controller() { return controller; }

    void awaitLoaded() throws Exception {
        await(() -> this.<Label>get("statusLabel").getText().endsWith(" vacancies"));
    }

    static void await(BooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            if (onFx(condition::getAsBoolean)) return;
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for GUI data");
    }

    static <T> T onFx(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action);
        Platform.runLater(task);
        return task.get(20, TimeUnit.SECONDS);
    }

    @Override public void close() throws Exception {
        onFx(() -> { controller.shutdown(); stage.close(); return null; });
        Platform.exit();
        System.clearProperty("seeker.gui.settings.path");
    }
}
