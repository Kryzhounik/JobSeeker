package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FxmlSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            try {
                FXMLLoader.load(FxmlSmoke.class.getResource(
                        "/com/seeker/guifx/main-view.fxml"));
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                finished.countDown();
            }
        }));
        if (!finished.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FXML did not load within 20 seconds.");
        }
        Platform.exit();
        if (failure.get() != null) {
            throw new IllegalStateException("FXML load failed", failure.get());
        }
        System.out.println("FXML load OK");
    }
}
