package com.seeker.guifx;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class FxmlSmoke {
    public static void main(String[] args) throws Exception {
        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.startup(() -> Platform.runLater(() -> {
            try {
                FXMLLoader loader = new FXMLLoader(FxmlSmoke.class.getResource(
                        "/com/seeker/guifx/main-view.fxml"));
                loader.load();
                Button batch = (Button) loader.getNamespace().get("batchButton");
                Button collect = (Button) loader.getNamespace().get("collectButton");
                Button config = (Button) loader.getNamespace().get("configButton");
                Button runs = (Button) loader.getNamespace().get("runsButton");
                Button companies = (Button) loader.getNamespace().get("companiesButton");
                Button refresh = (Button) loader.getNamespace().get("refreshButton");
                javafx.scene.control.Label statusLabel = (javafx.scene.control.Label) loader.getNamespace().get("statusLabel");
                Button blackTitles = (Button) loader.getNamespace().get("blackTitlesButton");
                if (!(statusLabel.getParent() instanceof WindowStatusBar footer)
                        || footer.getStatus() != statusLabel || footer.getLeft() != blackTitles) {
                    throw new AssertionError("Main window does not use the shared status bar");
                }
                Button refilter = (Button) loader.getNamespace().get("refilterButton");
                Button refilterDetail = (Button) loader.getNamespace().get("refilterDetailButton");
                HBox collectionGroup = (HBox) batch.getParent();
                HBox refilterGroup = (HBox) refilter.getParent();
                HBox actions = (HBox) collectionGroup.getParent();
                HBox filters = (HBox) refresh.getParent();
                if (actions.getChildren().getFirst() != collectionGroup
                        || !collectionGroup.getChildren().equals(java.util.List.of(batch, collect))
                        || !refilterGroup.getChildren().equals(java.util.List.of(refilter, refilterDetail))
                        || !collectionGroup.getStyleClass().contains("toolbar-button-group")
                        || !refilterGroup.getStyleClass().contains("toolbar-button-group")
                        || actions.getChildren().getLast() != config
                        || actions.getChildren().indexOf(runs) + 1 != actions.getChildren().indexOf(companies)
                        || !runs.getText().equals("Runs") || runs.getOnAction() == null
                        || actions == filters || filters.getChildren().getFirst() != refresh
                        || batch.getOnAction() == null
                        || !batch.getStyleClass().containsAll(java.util.List.of("collect-button", "batch-button"))
                        || loader.getNamespace().containsKey("searchButton")) {
                    throw new AssertionError("Toolbar order or Batch action/style changed");
                }
                checkApplyIsolation(loader);
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

    private static void checkApplyIsolation(FXMLLoader loader) throws Exception {
        MainController controller = loader.getController();
        JobRecord job = ClientData.readResponse("""
                {"job_id":123,"source_url":"https://example.com/job","easy_apply":false}
                """, JobRecord.class);
        set(controller, "selectedJob", job);
        set(controller, "detailLoadedJobId", 123);
        set(controller, "applyRunning", true);
        var buttons = MainController.class.getDeclaredMethod("setOperationButtonsDisabled", boolean.class);
        buttons.setAccessible(true);
        buttons.invoke(controller, false);
        Button apply = (Button) loader.getNamespace().get("applyButton");
        for (String name : java.util.List.of("linkedinCheckButton", "refilterButton", "refilterDetailButton")) {
            if (((Button) loader.getNamespace().get(name)).isDisabled()) {
                throw new AssertionError("Apply disabled unrelated action: " + name);
            }
        }
        if (((javafx.scene.Node) loader.getNamespace().get("statusActions")).isDisabled()
                || !apply.isDisabled()) {
            throw new AssertionError("Apply did not isolate its own busy state");
        }
        var detail = MainController.class.getDeclaredMethod("setDetailEnabled", boolean.class);
        detail.setAccessible(true);
        detail.invoke(controller, true);
        if (!apply.isDisabled()) throw new AssertionError("Detail refresh re-enabled active Apply");
        set(controller, "applyRunning", false);
        buttons.invoke(controller, true);
        if (apply.isDisabled()) throw new AssertionError("Unrelated operation disabled Apply");
    }

    private static void set(MainController controller, String name, Object value) throws Exception {
        var field = MainController.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(controller, value);
    }
}
