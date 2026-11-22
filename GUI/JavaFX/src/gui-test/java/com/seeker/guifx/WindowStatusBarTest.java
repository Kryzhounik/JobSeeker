package com.seeker.guifx;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindowStatusBarTest {
    @BeforeAll static void startJavaFx() { Platform.startup(() -> { }); }
    @AfterAll static void stopJavaFx() { Platform.exit(); }

    @Test void footerPlacementSurvivesResizeAndActionsDoNotOverlap() throws Exception {
        FxTestSupport.run(() -> {
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
                root.getStylesheets().add(WindowStatusBarTest.class.getResource(
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
    }
}
