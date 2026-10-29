package com.seeker.guifx;

import com.seeker.applier.ApplierRunner;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.PrintWriter;
import java.io.StringWriter;

final class ApplyProgressPane extends BackgroundProcessPane {
    private final TextField url = new TextField();
    private final TextArea resultArea = new TextArea();

    ApplyProgressPane(Area area) {
        super(area, "Apply");
        url.setEditable(false);
        url.setId("applyUrl");
        resultArea.setEditable(false);
        resultArea.setWrapText(true);
        resultArea.setPrefRowCount(12);
        resultArea.setId("applyResult");
        VBox content = new VBox(8, new Label("URL"), url, resultArea);
        content.setPadding(new Insets(12));
        VBox.setVgrow(resultArea, Priority.ALWAYS);
        setContent(content);
    }

    void begin(String url) {
        beginProcess();
        this.url.setText(url);
        resultArea.clear();
        resultArea.setPromptText("Preparing application...");
        setSummary("Preparing");
        setContext(url);
    }

    void complete(ApplierRunner.Result result) {
        String heading = result.submissionObserved() ? "Application may already have been submitted."
                : switch (result.status()) {
                    case "filled" -> "Filled.";
                    case "skipped" -> "Skipped.";
                    case "partially_filled" -> "Preparation incomplete.";
                    default -> result.status();
                };
        resultArea.setPromptText("");
        resultArea.setText(heading + "\n\n" + result.note());
        resultArea.positionCaret(0);
        setSummary(result.status());
        finishProcess(State.COMPLETED);
    }

    void fail(Throwable error) {
        StringWriter trace = new StringWriter();
        if (error != null) error.printStackTrace(new PrintWriter(trace));
        resultArea.setPromptText("");
        resultArea.setText(error == null ? "Unknown error" : trace.toString());
        resultArea.positionCaret(0);
        setSummary("Preparation failed");
        finishProcess(State.FAILED);
    }
}
