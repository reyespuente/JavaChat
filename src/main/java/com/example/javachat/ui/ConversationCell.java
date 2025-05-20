package com.example.javachat.ui;

import com.example.javachat.model.Conversation;
import javafx.scene.control.ListCell;


// Muestra el título de la conversación
public class ConversationCell extends ListCell<Conversation> {
    @Override
    protected void updateItem(Conversation conv, boolean empty) {
        super.updateItem(conv, empty);
        if (empty || conv == null) {
            setText(null);
            setGraphic(null);
        } else {
            setText(conv.getTitle());
        }
    }
}
