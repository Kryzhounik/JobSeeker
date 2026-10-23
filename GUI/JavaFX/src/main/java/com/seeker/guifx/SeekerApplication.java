package com.seeker.guifx;

import com.seeker.api.DriverApi;
import com.seeker.api.DriverConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

public final class SeekerApplication extends Application {
    private MainController controller;
    private AnnotationConfigApplicationContext context;

    @Override
    public void init() {
        context = new AnnotationConfigApplicationContext();
        context.registerBean(Path.class, ClientData::findProjectRoot);
        context.register(DriverConfiguration.class);
        context.refresh();
        context.registerShutdownHook();
    }

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                SeekerApplication.class.getResource("/com/seeker/guifx/main-view.fxml")
        );
        Scene scene = new Scene(loader.load(), 1440, 900);
        controller = loader.getController();
        controller.initializeData(context.getBean(DriverApi.class));
        scene.getStylesheets().add(
                SeekerApplication.class.getResource("/com/seeker/guifx/application.css")
                        .toExternalForm()
        );

        stage.setTitle("Seeker Jobs");
        stage.getIcons().add(new Image(
                SeekerApplication.class.getResourceAsStream("/com/seeker/guifx/SeekerJobsIcon.png")
        ));
        stage.setMinWidth(900);
        stage.setMinHeight(620);
        stage.setScene(scene);
        controller.attachStage(stage);
        stage.setOnCloseRequest(event -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }
}
