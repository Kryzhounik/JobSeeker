package com.seeker.guifx;

import com.seeker.chatevaluator.GptEvaluation;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.PrintWriter;
import java.io.StringWriter;

final class GptEvaluationProgressPane extends BackgroundProcessPane {
    private final String title;
    private final TextField url = new TextField();
    private final TextArea answer = new TextArea();
    private final TextArea errorDetails = new TextArea();
    private final VBox errorSection = new VBox(6, new Label("Error"), errorDetails);

    GptEvaluationProgressPane(Area area) {
        this(area, "GPT Eval");
    }

    GptEvaluationProgressPane(Area area, String title) {
        super(area, title);
        this.title = title;
        url.setEditable(false);
        url.setId("chatEvaluationUrl");
        answer.setEditable(false);
        answer.setWrapText(true);
        answer.setPrefRowCount(18);
        answer.setId("chatEvaluationAnswer");
        errorDetails.setEditable(false);
        errorDetails.setWrapText(true);
        errorDetails.setPrefRowCount(5);
        errorDetails.setId("chatEvaluationError");
        errorSection.setVisible(false);
        errorSection.setManaged(false);
        VBox content = new VBox(8, new Label("URL"), url, answer, errorSection);
        content.setPadding(new Insets(12));
        VBox.setVgrow(answer, Priority.ALWAYS);
        setContent(content);
    }

    void begin(String url) {
        beginProcess();
        this.url.setText(url);
        answer.clear();
        answer.setPromptText(title.equals("Letter") ? "Generating letter..." : "Evaluating...");
        errorDetails.clear();
        errorSection.setVisible(false);
        errorSection.setManaged(false);
        setSummary(title.equals("Letter") ? "Generating" : "Evaluating");
        setContext(url);
    }

    void complete(GptEvaluation.Result result) {
        complete(result.answer());
    }

    void complete(String text) {
        answer.setPromptText("");
        answer.setText(text);
        answer.positionCaret(0);
        setSummary("");
        finishProcess(State.COMPLETED);
    }

    void fail(Throwable error) {
        answer.setPromptText("");
        if (error instanceof GptEvaluation.SaveException saving) {
            answer.setText(saving.answer());
            answer.positionCaret(0);
        }
        StringWriter trace = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(trace));
        errorDetails.setText(error == null ? "Unknown error" : trace.toString());
        errorDetails.positionCaret(0);
        errorSection.setVisible(true);
        errorSection.setManaged(true);
        setSummary(error instanceof GptEvaluation.SaveException ? "Saving failed" : title + " failed");
        finishProcess(State.FAILED);
    }
}
