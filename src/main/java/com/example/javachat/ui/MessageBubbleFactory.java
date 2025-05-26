package com.example.javachat.ui;

import com.example.javachat.model.Message;
import com.example.javachat.session.SessionManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class MessageBubbleFactory {

    public static Node create(Message msg) {
        // Creamos un Text que sí rompe mid-word
        Text text = new Text(msg.getContent());
        text.getStyleClass().add("bubble-content");
        // fijo el ancho donde quiero que rompa líneas
        text.setWrappingWidth(300 - 16); // 300 px de burbuja menos 16 px de padding

        TextFlow textFlow = new TextFlow(text);
        // poner tañalño por defecto para el texflow y pueda incrementar
        textFlow.setMaxWidth(300 - 16);

        // timestamp como antes
        Label timestamp = new Label(msg.getSentAt());
        timestamp.getStyleClass().add("bubble-timestamp");

        // empaquetar en la burbuja
        VBox bubble = new VBox(textFlow, timestamp);
        bubble.setSpacing(4);
        bubble.getStyleClass().add(isSentByUser(msg) ? "bubble-sent" : "bubble-received");
        bubble.setPadding(new Insets(8));
        bubble.setMaxWidth(300);

        HBox container = new HBox(bubble);
        container.setPadding(new Insets(4,10,4,10));
        container.setAlignment(isSentByUser(msg) ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        return container;
    }

    private static boolean isSentByUser(Message msg) {
        return msg.getSenderId() == SessionManager.getInstance().getUserId();
    }
}
