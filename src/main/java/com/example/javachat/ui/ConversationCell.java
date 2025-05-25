package com.example.javachat.ui;

import com.example.javachat.model.Conversation;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.ListCell;


// Muestra el título de la conversación
public class ConversationCell extends ListCell<Conversation> {
    private final ChangeListener<Number> unreadListener = (obs, old, nw) -> updateDisplay();

    @Override
    protected void updateItem(Conversation conv, boolean empty) {
        super.updateItem(conv, empty);
        if (empty || conv == null) {
            setText(null);
            setGraphic(null);
        } else {
            // Desregistrar listener de la celda anterior
            if (getItem() != null) {
                getItem().unreadCountProperty().removeListener(unreadListener);
            }
            // Registrar listener para el nuevo ítem
            conv.unreadCountProperty().addListener(unreadListener);
            updateDisplay();
        }
    }

    private void updateDisplay() {
        Conversation conv = getItem();
        if (conv == null) return;
        String text = conv.getTitle();
        if (conv.getUnreadCount() > 0) {
            text += " *";
        }
        setText(text);
    }
}


