package com.example.javachat.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

// Modelo de conversación (directa o grupal).
public class Conversation {
    private final IntegerProperty id       = new SimpleIntegerProperty(this, "id");
    private final StringProperty  title    = new SimpleStringProperty(this, "title");
    private final StringProperty  type     = new SimpleStringProperty(this, "type", "direct");
    private final ObservableList<Message> messages = FXCollections.observableArrayList();

    public Conversation(int id, String title) {
        this.id.set(id);
        this.title.set(title);
    }

    public int getId()                   { return id.get(); }
    public IntegerProperty idProperty()  { return id; }

    public String getTitle()             { return title.get(); }
    public StringProperty titleProperty(){ return title; }

    public String getType()              { return type.get(); }
    public void setType(String t)        { this.type.set(t); }
    public StringProperty typeProperty(){ return type; }

    public ObservableList<Message> getMessages() { return messages; }
    public void addMessage(Message m)            { messages.add(m); }
}
