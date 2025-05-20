// Message.java
package com.example.javachat.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;

public class Message {
    private final IntegerProperty id        = new SimpleIntegerProperty(this, "id");
    private final IntegerProperty senderId  = new SimpleIntegerProperty(this, "senderId");
    private final StringProperty content    = new SimpleStringProperty(this, "content");
    private final StringProperty sentAt     = new SimpleStringProperty(this, "sentAt");

    public Message(int id, int senderId, String content, String sentAt) {
        this.id.set(id);
        this.senderId.set(senderId);
        this.content.set(content);
        this.sentAt.set(sentAt);
    }

    public int getId()             { return id.get(); }
    public int getSenderId()       { return senderId.get(); }
    public String getContent()     { return content.get(); }
    public String getSentAt()      { return sentAt.get(); }

    public IntegerProperty idProperty()         { return id; }
    public IntegerProperty senderIdProperty()   { return senderId; }
    public StringProperty contentProperty()     { return content; }
    public StringProperty sentAtProperty()      { return sentAt; }
}
