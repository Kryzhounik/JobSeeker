package com.seeker.guifx;

import com.seeker.chatevaluator.GptEvaluation;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
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
                StackPane overlay = new StackPane();
                GptEvaluationProgressPane pane = new GptEvaluationProgressPane(
                        new BackgroundProcessPane.Area(overlay, docks));
                pane.begin("https://example.com/42");
                new Scene(overlay, 1000, 800);
                overlay.resize(1000, 800);
                overlay.applyCss();
                overlay.layout();
                TextField url = (TextField) overlay.lookup("#chatEvaluationUrl");
                TextArea answerArea = (TextArea) overlay.lookup("#chatEvaluationAnswer");
                TextArea errorArea = (TextArea) overlay.lookup("#chatEvaluationError");
                ToggleButton badge = (ToggleButton) ((HBox) docks.getChildren().getFirst()).getChildren().getFirst();
                state(badge, "running");
                check(url.getText().equals("https://example.com/42"), "Panel lost original URL");
                pane.complete(result);
                state(badge, "completed");
                check(answerArea.getText().equals(answer), "Full answer is not displayed in the panel");
                pane.begin("https://example.com/43");
                state(badge, "running");
                pane.fail(new GptEvaluation.SaveException(answer, storageError));
                state(badge, "failed");
                check(answerArea.getText().equals(answer) && errorArea.getText().contains("disk full"),
                        "Storage failure did not retain the answer and error together");
                pane.begin("https://example.com/44");
                pane.fail(evaluationError);
                state(badge, "failed");
                check(answerArea.getText().isEmpty() && errorArea.getText().contains("evaluation failed"),
                        "Evaluation failure did not replace the old result");
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
