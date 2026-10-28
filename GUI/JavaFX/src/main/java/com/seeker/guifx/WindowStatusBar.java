package com.seeker.guifx;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

/** Shared footer: optional controls on the left, status text on the right. */
public final class WindowStatusBar extends BorderPane {
    public WindowStatusBar() {
        getStyleClass().add("toolbar");
        setPadding(new Insets(5, 10, 5, 10));
        setStatus(new Label());
    }

    public WindowStatusBar(Label status) {
        this();
        setStatus(status);
    }

    public Label getStatus() {
        return (Label) getRight();
    }

    public void setStatus(Label status) {
        if (!status.getStyleClass().contains("status-label")) {
            status.getStyleClass().add("status-label");
        }
        status.setAlignment(Pos.CENTER_RIGHT);
        status.setMinWidth(0);
        setAlignment(status, Pos.CENTER_RIGHT);
        setMargin(status, new Insets(0, 0, 0, 8));
        setRight(status);
    }
}
