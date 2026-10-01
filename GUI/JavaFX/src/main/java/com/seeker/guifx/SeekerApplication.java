package com.seeker.guifx;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public final class SeekerApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                SeekerApplication.class.getResource("/com/seeker/guifx/main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 720, 420);
        scene.getStylesheets().add(
                SeekerApplication.class.getResource("/com/seeker/guifx/application.css")
                        .toExternalForm()
        );

        stage.setTitle("Seeker Jobs");
        stage.setMinWidth(560);
        stage.setMinHeight(320);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
