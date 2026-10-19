package com.seeker.guifx;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class BackgroundProcessPaneSmoke {
    public static void main(String[] args) throws Exception {
        Platform.startup(() -> { });
        try {
            FutureTask<Void> check = new FutureTask<>(() -> {
                StackPane overlay = new StackPane();
                HBox docks = new HBox();
                BackgroundProcessPane.Area area = new BackgroundProcessPane.Area(overlay, docks);
                BackgroundProcessPane first = new BackgroundProcessPane(area, "First", "Run ID") { };
                BackgroundProcessPane second = new BackgroundProcessPane(area, "Second", "Run ID") { };
                ToggleButton firstDock = (ToggleButton) docks.getChildren().get(0);
                ToggleButton secondDock = (ToggleButton) docks.getChildren().get(1);
                Node firstPanel = overlay.getChildren().get(0);
                Node secondPanel = overlay.getChildren().get(1);

                first.beginProcess();
                first.setField("Run ID", "preserved-run");
                first.setSummary("7/100");
                assertState(firstDock, "running");
                first.setProcessState(BackgroundProcessPane.State.WAITING);
                assertState(firstDock, "waiting");
                if (!firstDock.getText().contains("waiting")) {
                    throw new AssertionError("Waiting state has no visible label");
                }
                second.beginProcess();
                if (firstPanel.isVisible() || !secondPanel.isVisible() || !secondDock.isSelected()) {
                    throw new AssertionError("Selecting a process did not collapse the previous panel");
                }
                firstDock.fire();
                if (!firstPanel.isVisible() || secondPanel.isVisible()) {
                    throw new AssertionError("Minimized progress could not be reopened");
                }
                first.setProcessState(BackgroundProcessPane.State.FAILED);
                assertState(firstDock, "failed");
                firstDock.fire();
                if (firstPanel.isVisible() || !first.fieldText("Run ID").equals("preserved-run")) {
                    throw new AssertionError("Minimizing failed progress lost its data");
                }
                first.setProcessState(BackgroundProcessPane.State.COMPLETED);
                assertState(firstDock, "completed");
                first.beginProcess();
                assertState(firstDock, "running");
                if (!first.fieldText("Run ID").isEmpty()) {
                    throw new AssertionError("A new run retained old progress data");
                }
                return null;
            });
            Platform.runLater(check);
            check.get(20, TimeUnit.SECONDS);
            System.out.println("Background process pane states and docking OK");
        } finally {
            Platform.exit();
        }
    }

    private static void assertState(Node node, String expected) {
        for (String state : new String[]{"running", "waiting", "failed", "completed"}) {
            if (node.getPseudoClassStates().contains(PseudoClass.getPseudoClass(state))
                    != state.equals(expected)) {
                throw new AssertionError("Incorrect process state: " + state);
            }
        }
    }
}
