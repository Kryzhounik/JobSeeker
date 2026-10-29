package com.seeker.guifx;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.event.Event;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import com.seeker.tools.api.ImmutableProgressSnapshot;
import com.seeker.collector.linkedin.collection.CollectionProgress;
import com.seeker.orchestrator.WorkflowProgress;
import com.seeker.codex.UsageLimitExceededException;
import com.seeker.applier.ApplierRunner;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

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
                HBox firstBadge = (HBox) docks.getChildren().get(0);
                HBox secondBadge = (HBox) docks.getChildren().get(1);
                ToggleButton firstDock = (ToggleButton) firstBadge.getChildren().get(0);
                ToggleButton secondDock = (ToggleButton) secondBadge.getChildren().get(0);
                Button badgeClose = (Button) firstBadge.getChildren().get(1);
                Node firstPanel = overlay.getChildren().get(0);
                Node secondPanel = overlay.getChildren().get(1);

                first.beginProcess();
                first.setField("Run ID", "preserved-run");
                first.setSummary("7/100");
                assertState(firstDock, "running");
                if (badgeClose.isVisible()) throw new AssertionError("Running process can be dismissed");
                first.setProcessState(BackgroundProcessPane.State.WAITING);
                assertState(firstDock, "waiting");
                if (!firstDock.getText().contains("waiting")) {
                    throw new AssertionError("Waiting state has no visible label");
                }
                if (badgeClose.isVisible()) throw new AssertionError("Active waiting process is dismissible");
                badgeClose.fire();
                if (!firstBadge.isVisible() || !firstPanel.isVisible()) {
                    throw new AssertionError("Waiting state implicitly finished the process");
                }
                second.beginProcess();
                if (firstPanel.isVisible() || !secondPanel.isVisible() || !secondDock.isSelected()) {
                    throw new AssertionError("Selecting a process did not collapse the previous panel");
                }
                firstDock.fire();
                if (!firstPanel.isVisible() || secondPanel.isVisible()) {
                    throw new AssertionError("Minimized progress could not be reopened");
                }
                first.finishProcess(BackgroundProcessPane.State.FAILED);
                assertState(firstDock, "failed");
                firstDock.fire();
                if (firstPanel.isVisible() || !first.fieldText("Run ID").equals("preserved-run")) {
                    throw new AssertionError("Minimizing failed progress lost its data");
                }
                first.finishProcess(BackgroundProcessPane.State.COMPLETED);
                assertState(firstDock, "completed");
                assertState(firstBadge, "completed");
                firstDock.fire();
                if (!badgeClose.isVisible()) throw new AssertionError("Finished badge has no dismiss button");
                badgeClose.fire();
                if (firstBadge.isVisible() || firstBadge.isManaged() || firstPanel.isVisible()) {
                    throw new AssertionError("Badge dismiss did not hide both views");
                }
                first.beginProcess();
                assertState(firstDock, "running");
                if (!firstBadge.isVisible() || !firstPanel.isVisible()
                        || !first.fieldText("Run ID").isEmpty() || badgeClose.isVisible()) {
                    throw new AssertionError("A new run retained old progress data");
                }
                first.finishProcess(BackgroundProcessPane.State.FAILED);
                HBox header = (HBox) ((VBox) firstPanel).getChildren().getFirst();
                ((Button) header.getChildren().getLast()).fire();
                if (firstBadge.isVisible() || firstPanel.isVisible()) {
                    throw new AssertionError("Panel dismiss did not hide both views");
                }
                checkSnapshotProgress(area);
                checkUsageLimits(area);
                checkResize();
                checkApplyProgress();
                return null;
            });
            Platform.runLater(check);
            check.get(20, TimeUnit.SECONDS);
            System.out.println("Background process pane states and docking OK");
        } finally {
            Platform.exit();
        }
    }

    private static void checkApplyProgress() {
        StackPane overlay = new StackPane();
        HBox docks = new HBox();
        ApplyProgressPane apply = new ApplyProgressPane(new BackgroundProcessPane.Area(overlay, docks));
        HBox badge = (HBox) docks.getChildren().getFirst();
        Button close = (Button) badge.getChildren().getLast();
        VBox panel = (VBox) overlay.getChildren().getFirst();
        apply.begin(123, "https://example.com/123");
        assertState(badge, "running");
        if (!badge.isVisible() || !panel.isVisible() || close.isVisible()
                || !apply.fieldText("Job ID").equals("123")
                || !apply.fieldText("URL").equals("https://example.com/123")) {
            throw new AssertionError("Apply does not show active work and its original vacancy");
        }
        apply.complete(new ApplierRunner.Result("partially_filled", "Inspect the open form.", false));
        assertState(badge, "completed");
        if (!apply.fieldText("Result").equals("partially_filled")
                || !details(panel).equals("Inspect the open form.") || !close.isVisible()) {
            throw new AssertionError("Apply result was lost or misrepresented");
        }
        close.fire();
        if (badge.isVisible() || panel.isVisible()) throw new AssertionError("Apply could not be dismissed");
        apply.begin(456, "https://example.com/456");
        assertState(badge, "running");
        if (!apply.fieldText("Result").isEmpty() || !details(panel).isEmpty()) {
            throw new AssertionError("Apply restart retained a previous result");
        }
        apply.fail(new IllegalStateException("Browser connection failed"));
        assertState(badge, "failed");
        if (!apply.fieldText("Job ID").equals("456")
                || !details(panel).equals("Browser connection failed") || !close.isVisible()) {
            throw new AssertionError("Apply failure lost vacancy context or error details");
        }
        apply.begin(789, "https://example.com/789");
        apply.complete(new ApplierRunner.Result("filled", "Review before submitting.", true));
        if (!details(panel).contains("may already have been submitted")) {
            throw new AssertionError("Apply submission warning was omitted");
        }
    }

    private static void checkResize() {
        StackPane overlay = new StackPane();
        HBox docks = new HBox();
        BackgroundProcessPane pane = new BackgroundProcessPane(
                new BackgroundProcessPane.Area(overlay, docks), "Resize", "Run ID") { };
        pane.beginProcess();
        new Scene(overlay, 1100, 850);
        overlay.getStylesheets().add(BackgroundProcessPaneSmoke.class.getResource(
                "/com/seeker/guifx/application.css").toExternalForm());
        overlay.resize(1100, 850);
        overlay.applyCss();
        overlay.layout();
        VBox panel = (VBox) overlay.getChildren().getFirst();
        double originalWidth = panel.getWidth();
        double originalHeight = panel.getHeight();
        drag(panel, 2, 20, -170, 0);
        overlay.layout();
        if (Math.abs(panel.getWidth() - originalWidth - 170) > 1
                || Math.abs(panel.getHeight() - originalHeight) > 1) {
            throw new AssertionError("Left-edge resize did not change only width");
        }
        TextArea details = (TextArea) content(panel).getChildren().getLast();
        double detailsHeight = details.getHeight();
        drag(panel, panel.getWidth() / 2, panel.getHeight() - 2, 0, 120);
        overlay.layout();
        if (Math.abs(panel.getHeight() - originalHeight - 120) > 1
                || details.getHeight() < detailsHeight + 119) {
            throw new AssertionError("Bottom-edge resize did not expand the Details area");
        }
        double width = panel.getWidth();
        double height = panel.getHeight();
        ToggleButton toggle = (ToggleButton) ((HBox) docks.getChildren().getFirst()).getChildren().getFirst();
        toggle.fire();
        toggle.fire();
        overlay.layout();
        if (panel.getWidth() != width || panel.getHeight() != height) {
            throw new AssertionError("Minimize/reopen lost manual panel size");
        }
        drag(panel, 2, panel.getHeight() - 2, -5000, 5000);
        overlay.layout();
        if (panel.getWidth() > 1084 || panel.getHeight() > 834) {
            throw new AssertionError("Corner resize escaped the main window");
        }
        overlay.resize(650, 420);
        overlay.layout();
        if (panel.getWidth() > 634 || panel.getHeight() > 404) {
            throw new AssertionError("Resized panel no longer fits a smaller main window");
        }
    }

    private static void drag(VBox panel, double x, double y, double dx, double dy) {
        var start = panel.localToScene(x, y);
        mouse(panel, MouseEvent.MOUSE_PRESSED, start.getX(), start.getY(), true);
        mouse(panel, MouseEvent.MOUSE_DRAGGED, start.getX() + dx, start.getY() + dy, true);
        mouse(panel, MouseEvent.MOUSE_RELEASED, start.getX() + dx, start.getY() + dy, false);
    }

    private static void mouse(VBox panel, javafx.event.EventType<MouseEvent> type,
            double sceneX, double sceneY, boolean down) {
        Event.fireEvent(panel, new MouseEvent(type, sceneX, sceneY, sceneX, sceneY,
                MouseButton.PRIMARY, 1, false, false, false, false, down, false, false,
                false, false, true, new PickResult(panel, sceneX, sceneY)));
    }

    private static void checkUsageLimits(BackgroundProcessPane.Area area) {
        SnapshotProgressPane resume = new SnapshotProgressPane(area, "Resume");
        resume.begin("resume-run");
        resume.fail(new IllegalStateException("Scope loading failed"));
        if (!resume.fieldText("Run ID").equals("resume-run")) {
            throw new AssertionError("Resume lost its Run ID before receiving progress");
        }
        Instant retry = Instant.parse("2026-11-01T12:34:56Z");
        String expectedTime = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm:ss z")
                .withZone(ZoneId.systemDefault()).format(retry);
        for (String title : List.of("Collect", "Batch", "Resume")) {
            SnapshotProgressPane pane = new SnapshotProgressPane(area, title);
            HBox badge = (HBox) area.docks().getChildren().getLast();
            Button close = (Button) badge.getChildren().getLast();
            VBox panel = (VBox) area.overlay().getChildren().getLast();
            Map<String, String> metrics = Map.of("Saved", "3", "Job facts", "7 / 10");
            for (Instant retryAt : new Instant[]{retry, null}) {
                pane.begin();
                pane.update(new ImmutableProgressSnapshot("limited-run", "Job facts", "Extracting facts",
                        "Job facts 7 / 10", metrics, "Last backend details"));
                pane.fail(new UsageLimitExceededException("Quota exhausted", retryAt));
                assertState(badge, "waiting");
                assertFields(panel, metrics);
                String text = details(panel);
                if (!pane.fieldText("Run ID").equals("limited-run")
                        || !pane.fieldText("Stage").equals("Job facts")
                        || !pane.fieldText("Action").equals("Extracting facts")
                        || !((ToggleButton) badge.getChildren().getFirst()).getText().contains("Job facts 7 / 10")
                        || !text.contains("Last backend details") || !text.contains("Quota exhausted")
                        || !close.isVisible() || !close.isManaged()) {
                    throw new AssertionError("Usage limit lost progress, reason, or dismiss control");
                }
                if (retryAt == null ? !text.contains("Retry time is unknown.") || text.contains("Retry at:")
                        : !text.contains("Retry at: " + expectedTime)) {
                    throw new AssertionError("Incorrect retry time: " + text);
                }
                if (retryAt == null) {
                    HBox header = (HBox) panel.getChildren().getFirst();
                    ((Button) header.getChildren().getLast()).fire();
                } else {
                    close.fire();
                }
                if (badge.isVisible() || panel.isVisible()) {
                    throw new AssertionError("Stopped waiting process cannot be dismissed");
                }
                pane.begin();
                assertState(badge, "running");
                if (!badge.isVisible() || !panel.isVisible() || close.isVisible()
                        || !details(panel).isEmpty() || !pane.fieldText("Run ID").isEmpty()) {
                    throw new AssertionError("Manual restart retained the usage-limit state");
                }
                assertFields(panel, Map.of());
                pane.update(new ImmutableProgressSnapshot("manual-run", "Backend stage", "Backend action",
                        "1 / 1", Map.of("Confirmed", "1"), "New details"));
                pane.complete(true);
                assertState(badge, "completed");
            }
            pane.begin();
            pane.fail(new IllegalStateException("Codex usage limit: Quota exhausted; retry at 12:00"));
            assertState(badge, "failed");
            if (!close.isVisible() || !details(panel).contains("Quota exhausted")
                    || details(panel).contains("Retry time is unknown.")) {
                throw new AssertionError("Ordinary failure was classified by its message");
            }
        }
    }

    private static void checkSnapshotProgress(BackgroundProcessPane.Area area) {
        SnapshotProgressPane batch = new SnapshotProgressPane(area, "Batch");
        HBox badge = (HBox) area.docks().getChildren().getLast();
        ToggleButton dock = (ToggleButton) badge.getChildren().getFirst();
        Button dismiss = (Button) badge.getChildren().getLast();
        batch.begin();
        Scene scene = new Scene(area.overlay(), 900, 1000);
        scene.getStylesheets().add(BackgroundProcessPaneSmoke.class.getResource(
                "/com/seeker/guifx/application.css").toExternalForm());
        VBox panel = (VBox) area.overlay().getChildren().getLast();
        for (int height : new int[]{1000, 900}) {
            area.overlay().resize(900, height);
            area.overlay().applyCss();
            area.overlay().layout();
            double expectedHeight = Math.min(panel.prefHeight(panel.getWidth()), height - 16);
            if (Math.abs(panel.getHeight() - expectedHeight) > 1
                    || panel.getHeight() >= height - 100) {
                throw new AssertionError("Progress panel stretches beyond its content");
            }
            ScrollPane scroll = (ScrollPane) panel.getChildren().get(1);
            boolean scrolling = scroll.lookupAll(".scroll-bar").stream()
                    .filter(node -> node instanceof ScrollBar)
                    .map(node -> (ScrollBar) node)
                    .anyMatch(bar -> bar.getParent() == scroll
                            && bar.getOrientation() == javafx.geometry.Orientation.VERTICAL
                            && bar.isVisible());
            if (scrolling) throw new AssertionError("Batch fields still require vertical scrolling at "
                    + height + "; viewport=" + scroll.getViewportBounds()
                    + "; content=" + scroll.getContent().getLayoutBounds());
        }
        Map<String, String> initial = new LinkedHashMap<>();
        initial.put("Z first field", "7 / 100");
        initial.put("A second field", "25");
        batch.update(new WorkflowProgress("batch-run", "Unknown stage", "Unknown action",
                "7 / 100", initial, "Backend details"));
        if (!dock.getText().equals("Batch: 7 / 100")) {
            throw new AssertionError("Badge shows an action instead of backend progress: " + dock.getText());
        }
        assertFields(panel, initial);
        area.overlay().applyCss();
        area.overlay().layout();
        double compactHeight = panel.getHeight();
        Map<String, String> collectionFields = new LinkedHashMap<>();
        for (int i = 0; i < 13; i++) collectionFields.put("Metric " + i, "0");
        batch.update(new ImmutableProgressSnapshot("batch-run", "Collection", "Working",
                "Collection 7 / 100", collectionFields, "Details"));
        area.overlay().applyCss();
        area.overlay().layout();
        TextArea detailsArea = (TextArea) content(panel).getChildren().getLast();
        if (panel.getHeight() <= compactHeight || panel.getHeight() >= 800
                || detailsArea.getHeight() > 140) {
            throw new AssertionError("Collection panel has excessive empty space: " + panel.getHeight());
        }
        batch.update(new WorkflowProgress("batch-run", "Unknown stage", "Unknown action",
                "7 / 100", initial, "Backend details"));
        area.overlay().applyCss();
        area.overlay().layout();
        if (Math.abs(panel.getHeight() - compactHeight) > 1) {
            throw new AssertionError("Panel did not shrink after fields were removed");
        }
        assertState(badge, "running");
        if (dismiss.isVisible()) throw new AssertionError("A snapshot finished the operation");
        Map<String, String> next = new LinkedHashMap<>();
        next.put("A second field", "123");
        next.put("New metric", "6 / 20");
        batch.update(new ImmutableProgressSnapshot("batch-run", "Next stage", "Next action", "6 / 20", next,
                "Details with URL: https://example.com/job"));
        assertFields(panel, next);
        Map<String, String> reordered = new LinkedHashMap<>();
        reordered.put("New metric", "9 / 20");
        reordered.put("A second field", "456");
        batch.update(new ImmutableProgressSnapshot("batch-run", "Next stage", "Next action", "9 / 20", reordered,
                "Details with URL: https://example.com/job"));
        assertFields(panel, reordered);
        batch.fail(new IllegalStateException("analysis failed"));
        assertState(badge, "failed");
        if (!batch.fieldText("Run ID").equals("batch-run")
                || !batch.fieldText("Stage").equals("Next stage")
                || !batch.fieldText("Action").equals("Next action") || !dismiss.isVisible()
                || !details(panel).contains("https://example.com/job")
                || !details(panel).contains("analysis failed")) {
            throw new AssertionError("Batch failure lost progress or cannot be dismissed");
        }
        assertFields(panel, reordered);
        batch.begin();
        assertFields(panel, Map.of());
        if (!batch.fieldText("Run ID").isEmpty() || !details(panel).isEmpty()) {
            throw new AssertionError("Restart retained old snapshot");
        }
        batch.update(new ImmutableProgressSnapshot("next-run", "Final backend stage", "Final backend action",
                "20 / 20", initial, "Final backend details"));
        assertState(badge, "running");
        batch.complete(true);
        assertState(badge, "completed");
        assertFields(panel, initial);
        if (!batch.fieldText("Stage").equals("Final backend stage")
                || !batch.fieldText("Action").equals("Final backend action")
                || !details(panel).equals("Final backend details") || !dock.getText().contains("done")) {
            throw new AssertionError("Completion replaced the backend snapshot");
        }
        dismiss.fire();
        if (badge.isVisible()) throw new AssertionError("Completed batch cannot be dismissed");

        SnapshotProgressPane collect = new SnapshotProgressPane(area, "Collect");
        collect.begin();
        collect.update(new CollectionProgress("collect-run", "Same stage", "Same action", "6 / 20", next, ""));
        VBox collectPanel = (VBox) area.overlay().getChildren().getLast();
        assertFields(collectPanel, next);
        collect.update(new ImmutableProgressSnapshot("collect-run", "No metrics", "Still working", "", Map.of(), ""));
        assertFields(collectPanel, Map.of());
        collect.update(new ImmutableProgressSnapshot("collect-run", "Same stage", "Same action", "6 / 20", next, ""));
        collect.complete(false);
        assertFields(collectPanel, next);
        assertState(area.docks().getChildren().getLast(), "failed");
        collect.begin();
        collect.update(new ImmutableProgressSnapshot("empty-fields", "Stage", "Action", "", Map.of(), ""));
        assertFields(collectPanel, Map.of());
    }

    private static VBox content(VBox panel) {
        return (VBox) ((ScrollPane) panel.getChildren().get(1)).getContent();
    }

    private static String details(VBox panel) {
        return ((TextArea) content(panel).getChildren().getLast()).getText();
    }

    private static void assertFields(VBox panel, Map<String, String> expected) {
        GridPane grid = (GridPane) content(panel).getChildren().get(1);
        List<String> labels = grid.getChildren().stream().filter(Label.class::isInstance)
                .map(Label.class::cast).map(Label::getText).toList();
        List<String> values = grid.getChildren().stream().filter(TextField.class::isInstance)
                .map(TextField.class::cast).map(TextField::getText).toList();
        if (!labels.equals(List.copyOf(expected.keySet())) || !values.equals(List.copyOf(expected.values()))) {
            throw new AssertionError("Snapshot field replacement/order mismatch: " + labels + " " + values);
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
