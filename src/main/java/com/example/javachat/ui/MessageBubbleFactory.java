package com.example.javachat.ui;

import com.example.javachat.model.Message;
import com.example.javachat.session.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/**
 * Crea un nodo (HBox) con estilo de “burbuja” para un mensaje.
 * Alinea a la derecha si es enviado por el usuario actual, o a la izquierda si no.
 */
public class MessageBubbleFactory {

    public static Node create(Message msg) {
        // Crea la etiqueta con el contenido y fecha
        Label text = new Label(msg.getContent());
        text.setWrapText(true);
        text.getStyleClass().add("bubble-content");

        Label timestamp = new Label(msg.getSentAt());
        timestamp.getStyleClass().add("bubble-timestamp");

        // Contenedor vertical para mensaje + hora
        javafx.scene.layout.VBox bubble = new javafx.scene.layout.VBox(text, timestamp);
        bubble.setSpacing(4);
        bubble.getStyleClass().add(isSentByUser(msg) ? "bubble-sent" : "bubble-received");
        bubble.setPadding(new Insets(8));
        bubble.setMaxWidth(300);

        // HBox para alinear burbuja
        HBox container = new HBox(bubble);
        container.setPadding(new Insets(4, 10, 4, 10));
        container.setAlignment(isSentByUser(msg) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        return container;
    }

    private static boolean isSentByUser(Message msg) {
        return msg.getSenderId() == SessionManager.getInstance().getUserId();
    }
}
