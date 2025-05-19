package com.example.javachat.controller;

import com.example.javachat.factory.WindowFactory;
import com.example.javachat.service.ApiService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;


public class LoginController {
    @FXML private TextField    usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label         errorLabel;

    private final ApiService api = ApiService.getInstance();

    @FXML
    private void onLoginClicked() {
        String user = usernameField.getText().trim();
        String pass = passwordField.getText();

        if (user.isEmpty() || pass.isEmpty()) {
            errorLabel.setText("Usuario y contraseña obligatorios");
            return;
        }

        errorLabel.setText("Iniciando sesión…");
        // HILO PARA NO BLOQUEAR LA INTERFAZ!!!
        new Thread(() -> {
            try {
                boolean ok = api.loginUser(user, pass);
                Platform.runLater(() -> {
                    if (ok) {
                        openChatScene();
                    }
                });
            } catch (IOException e) {
                String msg = e.getMessage();
                Platform.runLater(() -> errorLabel.setText(msg));
            }
        }).start();
    }

    private void openChatScene() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/javachat/fxml/Chat.fxml")
            );
            Stage stage = (Stage) usernameField.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("JavaChat – Chats");
            stage.setMinWidth(600);
            stage.setMinHeight(400);
        } catch (Exception ex) {
            ex.printStackTrace();
            errorLabel.setText("No se pudo abrir la ventana de chats");
        }
    }

    @FXML
    private void onRegisterClicked() {
        WindowFactory.open("/com/example/javachat/fxml/Register.fxml",
                "Registro – JavaChat");
    }
}
