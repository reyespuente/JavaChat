module com.example.javachat {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.google.gson;
    requires java.desktop;
    requires java.prefs;

    opens com.example.javachat to javafx.fxml;
    opens com.example.javachat.controller to javafx.fxml;
    opens com.example.javachat.service to com.google.gson;

    exports com.example.javachat;
}