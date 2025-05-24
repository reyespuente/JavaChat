package com.example.javachat;

import com.example.javachat.service.ApiService;
import com.example.javachat.session.SessionManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        SessionManager sm = SessionManager.getInstance();
        ApiService api = ApiService.getInstance();
        // intentar cargar una sesion si se guardo
        boolean hasSession = sm.loadFromPrefs();
        if (hasSession) {
            // si hay un token válido se usa
            api.setToken(sm.getToken());
            // por lo tanto se abre el chat directamente
            Parent chatRoot = FXMLLoader.load(getClass().getResource("/com/example/javachat/fxml/Chat.fxml"));
            primaryStage.setScene(new Scene(chatRoot));
            primaryStage.setTitle("JavaChat – Chats");
        } else {
            // si el token JWT no es válido o no hay sesión guardada, se abre el login
            Parent loginRoot = FXMLLoader.load(getClass().getResource("/com/example/javachat/fxml/Login.fxml"));
            primaryStage.setScene(new Scene(loginRoot));
            primaryStage.setTitle("JavaChat – Iniciar sesión");
        }

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
