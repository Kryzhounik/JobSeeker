package com.seeker.guifx;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class WindowStatusBarSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        try {
            FutureTask<Void> check = new FutureTask<>(() -> {
                Label count = new Label("17 runs");
                WindowStatusBar status = new WindowStatusBar(count);
                TextArea details = new TextArea("Run details");
                details.setPrefRowCount(4);
                TableView<Object> table = new TableView<>();
                table.setMinHeight(100);
                details.setMinHeight(50);
                SplitPane split = new SplitPane(table, details);
                split.setOrientation(javafx.geometry.Orientation.VERTICAL);
                split.setDividerPositions(0.82);
                BorderPane root = new BorderPane(split, null, null, status, null);
                new Scene(root, 1000, 620);
                root.getStylesheets().add(WindowStatusBarSmoke.class.getResource(
                        "/com/seeker/guifx/application.css").toExternalForm());
                for (int width : new int[]{700, 1250}) {
                    root.resize(width, 620);
                    root.applyCss();
                    root.layout();
                    double right = count.getLayoutX() + count.getWidth();
                    if (Math.abs(right - (status.getWidth() - 10)) > 1
                            || Math.abs(status.localToScene(0, status.getHeight()).getY() - 620) > 1
                            || !status.getStyleClass().contains("toolbar")
                            || !count.getStyleClass().contains("status-label")) {
                        throw new AssertionError("Count is not in the shared bottom-right footer");
                    }
                }
                double originalDetailsHeight = details.getHeight();
                double originalTableHeight = table.getHeight();
                split.setDividerPositions(0.5);
                root.layout();
                if (details.getHeight() <= originalDetailsHeight + 100
                        || table.getHeight() >= originalTableHeight - 100
                        || Math.abs(status.localToScene(0, status.getHeight()).getY() - 620) > 1) {
                    throw new AssertionError("Runs divider did not resize Details independently of the footer");
                }
                Button action = new Button("Black titles");
                status.setLeft(action);
                count.setText("773 vacancies");
                root.applyCss();
                root.layout();
                if (action.getBoundsInParent().getMaxX() >= count.getBoundsInParent().getMinX()) {
                    throw new AssertionError("Left action overlaps the status text");
                }
                return null;
            });
            Platform.runLater(check);
            check.get(20, TimeUnit.SECONDS);
            System.out.println("Shared window status bar placement OK");
        } finally {
            Platform.exit();
        }
    }
}
