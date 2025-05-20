// Conversation.java
package com.example.javachat.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class Conversation {
    private final IntegerProperty id      = new SimpleIntegerProperty(this, "id");
    private final StringProperty title    = new SimpleStringProperty(this, "title");
    private final ObservableList<Message> messages = FXCollections.observableArrayList();

    public Conversation(int id, String title) {
        this.id.set(id);
        this.title.set(title);
    }

    public int getId()              { return id.get(); }
    public String getTitle()        { return title.get(); }
    public ObservableList<Message> getMessages() { return messages; }

    public IntegerProperty idProperty()     { return id; }
    public StringProperty titleProperty()   { return title; }

    /** Añade un mensaje nuevo al final de la conversación */
    public void addMessage(Message m) {
        messages.add(m);
    }
}
