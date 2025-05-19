package com.example.javachat;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws Exception {

        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/example/javachat/fxml/Login.fxml")
        );
        Scene scene = new Scene(loader.load());
        stage.setTitle("JavaChat — Login");
        stage.setScene(scene);
        stage.setMinWidth(360);
        stage.setMinHeight(480);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
