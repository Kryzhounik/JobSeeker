package com.seeker.guifx;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.Stage;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class BackgroundTasks {
    @FunctionalInterface interface Work<T> {
        T run(Consumer<String> updateMessage) throws Exception;
    }

    private final ReadOnlyBooleanWrapper running = new ReadOnlyBooleanWrapper();
    private final Consumer<String> message;
    private final Supplier<Stage> owner;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "gui-long-operation");
        thread.setDaemon(true);
        return thread;
    });

    BackgroundTasks(Consumer<String> message, Supplier<Stage> owner) {
        this.message = message;
        this.owner = owner;
    }

    boolean isRunning() { return running.get(); }
    ReadOnlyBooleanProperty runningProperty() { return running.getReadOnlyProperty(); }

    <T> void run(String initialMessage, Callable<T> work, Consumer<T> success, String errorTitle) {
        runWithProgress(initialMessage, updater -> work.call(), success, errorTitle);
    }

    <T> void runWithProgress(String initialMessage, Work<T> work, Consumer<T> success, String errorTitle) {
        if (running.get()) return;
        running.set(true);
        Task<T> task = new Task<>() {
            @Override protected T call() throws Exception {
                updateMessage(initialMessage);
                return work.run(this::updateMessage);
            }
        };
        task.messageProperty().addListener((obs, old, text) -> {
            if (text != null && !text.isBlank()) message.accept(text);
        });
        task.setOnSucceeded(event -> {
            running.set(false);
            success.accept(task.getValue());
        });
        task.setOnFailed(event -> {
            running.set(false);
            message.accept(errorTitle);
            String detail = task.getException() == null ? "Unknown error"
                    : String.valueOf(task.getException().getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR, detail);
            alert.setTitle(errorTitle);
            alert.initOwner(owner.get());
            alert.show();
        });
        executor.execute(task);
    }

    void shutdown() { executor.shutdownNow(); }
}

