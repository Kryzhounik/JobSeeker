package com.seeker.guifx;

import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class GptEvaluationSmoke {
    public static void main(String[] args) throws Exception {
        List<String> calls = new ArrayList<>();
        List<JobGptEvaluation> saved = new ArrayList<>();
        String answer = "Полный ответ\n\n" + "Java — подходит.\n".repeat(1000);
        GptEvaluation evaluation = new GptEvaluation(url -> {
            calls.add(url);
            check(saved.isEmpty(), "Storage accessed before evaluation finished");
            return answer;
        }, (id, text, time) -> saved.add(new JobGptEvaluation(id, text, time)));
        JobGptEvaluation result = evaluation.run(42, "https://example.com/42");
        check(calls.equals(List.of("https://example.com/42")), "Wrong URL or repeated evaluation");
        check(saved.equals(List.of(result)) && result.jobId() == 42
                && result.answer().equals(answer), "Result was truncated or saved under another ID");
        Instant.parse(result.evaluatedAt());
        JobGptEvaluation loaded = ClientData.readResponse("""
                {"job_id":42,"answer":"Полный ответ\\nJava", "evaluated_at":"2026-10-22T14:00:00Z"}
                """, JobGptEvaluation.class);
        check(loaded.jobId() == 42 && loaded.answer().equals("Полный ответ\nJava")
                        && loaded.evaluatedAt().equals("2026-10-22T14:00:00Z"),
                "Saved evaluation JSON was not mapped for ID-based reading");

        AtomicInteger attempts = new AtomicInteger();
        IOException storageError = new IOException("disk full");
        try {
            new GptEvaluation(url -> { attempts.incrementAndGet(); return answer; },
                    (id, text, time) -> { throw storageError; }).run(42, "https://example.com/42");
            throw new AssertionError("Saving failure reported as success");
        } catch (GptEvaluation.SaveException error) {
            check(error.getCause() == storageError && error.answer().equals(answer)
                    && error.getMessage().contains("saving failed") && attempts.get() == 1,
                    "Saving failure lost answer, mislabeled error or resent URL");
        }
        IOException evaluationError = new IOException("evaluation failed");
        try {
            new GptEvaluation(url -> { throw evaluationError; },
                    (id, text, time) -> { throw new AssertionError("Saving after failed evaluation"); })
                    .run(42, "https://example.com/42");
            throw new AssertionError("Evaluation failure reported as success");
        } catch (IOException error) {
            check(error == evaluationError, "Evaluation failure mislabeled as storage failure");
        }

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
