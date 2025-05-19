package com.example.javachat.controller;

import com.example.javachat.service.ApiService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.paint.Color;

import java.io.IOException;

public class RegisterController {
    @FXML private TextField nombreUsuario;
    @FXML private TextField nombreCompleto;
    @FXML private PasswordField passwd;
    @FXML private PasswordField passwdConfirmacion;
    @FXML private Label infoLabel;

    private final ApiService api = ApiService.getInstance();

    @FXML
    private void onRegisterSubmit() {
        String user    = nombreUsuario.getText().trim();
        String name    = nombreCompleto.getText().trim();
        String pass    = passwd.getText();
        String confirm = passwdConfirmacion.getText();

        if (user.isEmpty() || name.isEmpty() || pass.isEmpty() || confirm.isEmpty()) {
            showError("Todos los campos son obligatorios.");
            return;
        }
        if (!pass.equals(confirm)) {
            showError("Las contraseñas no coinciden.");
            return;
        }

        showInfo("Registrando…");
        new Thread(() -> {
            try {
                int newId = api.registerUser(user, pass, name);
                Platform.runLater(() -> {
                    if (newId > 0) {
                        showSuccess("Usuario creado exitosamente. Ahora puede cerrar esta ventana e iniciar sesión.");
                    } else {
                        showError("Error al registrar: usuario existe.");
                    }
                });
            } catch (IOException e) {
                String msg = e.getMessage();
                String userMsg = "Error de red o servidor.";
                try {
                    int brace = msg.indexOf('{');
                    if (brace >= 0) {
                        String json = msg.substring(brace);
                        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
                        if (obj.has("error")) {
                            userMsg = obj.get("error").getAsString();
                        }
                    }
                } catch (Exception ex) {
                }
                final String display = userMsg;
                Platform.runLater(() -> showError(display));
            }
        }).start();
    }

    private void showError(String text) {
        infoLabel.setTextFill(Color.RED);
        infoLabel.setText(text);
    }

    private void showInfo(String text) {
        infoLabel.setTextFill(Color.GRAY);
        infoLabel.setText(text);
    }

    private void showSuccess(String text) {
        infoLabel.setTextFill(Color.web("#4CAF50"));
        infoLabel.setText(text);
    }
}
