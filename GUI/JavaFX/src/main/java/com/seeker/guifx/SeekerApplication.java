package com.seeker.guifx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class SeekerApplication extends Application {
    private MainController controller;

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                SeekerApplication.class.getResource("/com/seeker/guifx/main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 1440, 900);
        controller = loader.getController();
        scene.getStylesheets().add(
                SeekerApplication.class.getResource("/com/seeker/guifx/application.css")
                        .toExternalForm()
        );

        stage.setTitle("Seeker Jobs");
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
