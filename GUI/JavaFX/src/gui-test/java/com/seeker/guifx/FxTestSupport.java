package com.seeker.guifx;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

final class FxTestSupport {
    private FxTestSupport() { }

    static void run(Callable<Void> scenario) throws Exception {
        FutureTask<Void> task = new FutureTask<>(scenario);
        Platform.runLater(task);
        task.get(20, TimeUnit.SECONDS);
    }
}
