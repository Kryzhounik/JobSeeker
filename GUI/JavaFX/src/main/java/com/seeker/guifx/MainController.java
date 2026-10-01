package com.seeker.guifx;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public final class MainController {
    @FXML
    private Label launchStatus;

    @FXML
    private void initialize() {
        launchStatus.setText("JavaFX launch ready");
    }
}
