package com.example.javachat.factory;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class WindowFactory {
    public static void open(String fxmlPath, String title) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    WindowFactory.class.getResource(fxmlPath)
            );
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setScene(new Scene(root));
            stage.setTitle(title);
            stage.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
