package com.seeker.guifx;

import com.seeker.chatevaluator.GptEvaluation;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

public final class GptEvaluationSmoke {
    public static void main(String[] args) throws Exception {
        String answer = "Полный ответ\n\n" + "Java — подходит.\n".repeat(1000);
        GptEvaluation.Result result = new GptEvaluation.Result(42, answer, "2026-10-22T14:00:00Z");
        GptEvaluation.Result loaded = ClientData.readResponse("""
                {"job_id":42,"answer":"Полный ответ\\nJava", "evaluated_at":"2026-10-22T14:00:00Z"}
                """, GptEvaluation.Result.class);
        check(loaded.jobId() == 42 && loaded.answer().equals("Полный ответ\nJava")
                        && loaded.evaluatedAt().equals("2026-10-22T14:00:00Z"),
                "Saved evaluation JSON was not mapped for ID-based reading");

        IOException storageError = new IOException("disk full");
        IOException evaluationError = new IOException("evaluation failed");

        Platform.startup(() -> { });
        try {
            FutureTask<Void> panelChecks = new FutureTask<>(() -> {
                HBox docks = new HBox();
                GptEvaluationProgressPane pane = new GptEvaluationProgressPane(
                        new BackgroundProcessPane.Area(new StackPane(), docks));
                ToggleButton badge = (ToggleButton) ((HBox) docks.getChildren().getFirst()).getChildren().getFirst();
                pane.begin(42, "https://example.com/42");
                state(badge, "running");
                check(pane.fieldText("Job ID").equals("42"), "Panel lost original ID");
                pane.complete(result);
                state(badge, "completed");
                check(pane.fieldText("Stage").equals("Saved"), "Success before storage completion");
                pane.begin(43, "https://example.com/43");
                state(badge, "running");
                pane.fail(new GptEvaluation.SaveException(answer, storageError));
                state(badge, "failed");
                check(pane.fieldText("Stage").equals("Saving failed"), "Storage error mislabeled");
                pane.begin(44, "https://example.com/44");
                pane.fail(evaluationError);
                state(badge, "failed");
                check(pane.fieldText("Stage").equals("Evaluation failed"), "Evaluation error mislabeled");
                return null;
            });
            Platform.runLater(panelChecks);
            panelChecks.get(15, TimeUnit.SECONDS);
        } finally {
            Platform.exit();
        }
        System.out.println("GPT evaluation execution and progress checks OK");
    }

    private static void state(ToggleButton badge, String value) {
        check(badge.getPseudoClassStates().contains(PseudoClass.getPseudoClass(value)),
                "Missing state: " + value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
