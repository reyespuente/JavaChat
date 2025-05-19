package com.example.javachat.controller;

import com.example.javachat.factory.WindowFactory;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;

import java.io.IOException;

public class LoginController {
    @FXML TextField    usernameField;
    @FXML PasswordField passwordField;
    @FXML Label         errorLabel;

    @FXML
    private void onLoginClicked() {
        // Por ahora no hace nada
        errorLabel.setText("Tralalelo Tralala");
    }


    @FXML
    private void onRegisterClicked() {
        WindowFactory.open("/com/example/javachat/fxml/Register.fxml",
                "Registro – JavaChat");
    }

}
