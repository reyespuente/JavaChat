package com.example.javachat.controller;

import com.example.javachat.model.Conversation;
import com.example.javachat.model.Message;
import com.example.javachat.model.User;
import com.example.javachat.service.ApiService;
import com.example.javachat.ui.ConversationCell;
import com.example.javachat.ui.MessageBubbleFactory;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatController {
    // Left pane
    @FXML private TabPane tabPane;
    @FXML private ListView<Conversation> convoList;  // LA CONVERSACIÓN!!!
    // Center pane
    @FXML private Label    convoTitle;
    @FXML private VBox     messagesBox;
    @FXML private TextArea messageField;
    @FXML private Label errorLabel;

    // Nuevos para Contactos
    @FXML private TextField    newContactField;
    @FXML private ListView<User> contactsList;  // User = modelo de usuario (id, username, nombre)

    @FXML private ScrollPane messageScrollPane; // para mantenel siempre los mensajes ultimos visibles


    private Conversation currentConv;
    private int currentConversationId = 0;

    private final Map<Integer, String> lastTimestamp = new HashMap<>();


    @FXML
    public void initialize() {
        // list view de las conversaciones
        convoList.setCellFactory(lv -> new ConversationCell());

        // creando un hilo para la carga de conversaciones
        new Thread(() -> {
            try {
                var convs = ApiService.getInstance().getConversations();
                Platform.runLater(() -> {
                    convoList.getItems().setAll(convs);
                    if (!convs.isEmpty()) {
                        // por defecto cargar la primer aconversación
                        convoList.getSelectionModel().select(0);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // si cambiamos la conversación seleccionada, guarda el ID y carga mensajes
        convoList.getSelectionModel().selectedItemProperty().addListener((obs, old, conv) -> {
            if (conv != null) {
                currentConversationId = conv.getId();
                loadConversation(conv);
            }
        });

        // AQUI SE AJUSTA EL TIEMPO DE ACTUALIZACION! se uso polling
        Timeline refresher = new Timeline(
                new KeyFrame(Duration.seconds(3), e -> {
                    Conversation sel = convoList.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        loadConversation(sel);
                    }
                })
        );
        refresher.setCycleCount(Animation.INDEFINITE);
        refresher.play();

        //ListView de contactos
        contactsList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getDisplayName());
            }
        });
        contactsList.getSelectionModel().selectedItemProperty().addListener((obs, old, u) -> {
            if (u != null) {
                startChatWith(u.getId());
                // Cambia a la pestaña de Chats
                tabPane.getSelectionModel().select(0);
            }
        });

        //Listener para volver a renderizar al regresar a la pestaña "Chats"
        tabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if ("Chats".equals(newTab.getText())) {
                Conversation sel = convoList.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    // Vuelve a pintar el historial que ya está en el modelo
                    renderMessages(sel);
                }
            }
        });

        //carga inicial de contactos
        loadContacts();
    }



    private void loadConversation(Conversation conv) {
        int convId = conv.getId();
        convoTitle.setText(conv.getTitle());

        // Si es la primera carga limpiamos todo si no solo añadimos nuevos
        boolean firstLoad = !lastTimestamp.containsKey(convId);
        if (firstLoad) {
            messagesBox.getChildren().clear();
        }

        String since = lastTimestamp.getOrDefault(convId, "2025-01-01 00:00:00");

        new Thread(() -> {
            try {
                // SOLO los mensajes nuevos
                List<Message> nuevos = ApiService.getInstance()
                        .getMessages(convId, since);

                // el mayor timestamp que llega
                String maxTs = since;
                for (Message m : nuevos) {
                    if (firstLoad) {
                        conv.getMessages().add(m);
                    } else {
                        conv.addMessage(m);
                    }
                    // convertimos el String del mensaje a comparar
                    if (m.getSentAt().compareTo(maxTs) > 0) {
                        maxTs = m.getSentAt();
                    }
                }

                final String newSince = maxTs;
                Platform.runLater(() -> {
                    // Añadimos las burbujas de los nuevos mensajes
                    for (Message m : nuevos) {
                        messagesBox.getChildren().add(MessageBubbleFactory.create(m));
                    }
                    // Guardamos para la próxima llamada
                    lastTimestamp.put(convId, newSince);
                });

            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
        renderMessages(conv);
    }


    @FXML
    private void onSendClicked() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || currentConversationId == 0) {
            System.out.println("Nada que enviar o sin conversación seleccionada");
            return;
        }

        // ERROR CORREGIDO DE DOBLEMENSAJE
        messageField.setDisable(true);
        System.out.println("Enviando mensaje: \"" + text + "\" a conv " + currentConversationId);

        new Thread(() -> {
            try {
                boolean ok = ApiService.getInstance().sendMessage(currentConversationId, text);
                System.out.println("sendMessage returned: " + ok);
                Platform.runLater(() -> {
                    messageField.setDisable(false);
                    if (ok) {
                        messageField.clear();
                        // Recarga la conversación para ver el nuevo mensaje
                        Conversation conv = convoList.getSelectionModel().getSelectedItem();
                        if (conv != null) {
                            loadConversation(conv);
                        }
                    } else {
                        System.err.println("Error: sendMessage devolvió false");
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    messageField.setDisable(false);
                });
            }
        }).start();
    }



    @FXML
    private void onInfoClicked() {
        // PENDIENTE ////////////////////////
    }

    //CARGAR CONTACTOS CON EL ENDPOINT
    private void loadContacts() {
        new Thread(() -> {
            try {
                List<User> list = ApiService.getInstance().listContacts();
                Platform.runLater(() -> contactsList.getItems().setAll(list));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // agregar contacto
    @FXML
    private void onAddContactClicked() {
        String username = newContactField.getText().trim();
        if (username.isEmpty()) {
            return;
        }
        newContactField.setDisable(true);
        new Thread(() -> {
            try {
                //ID del usuario por username
                int otherId = ApiService.getInstance().getUserIdByUsername(username);
                // enviar la solicitud de contacto
                boolean ok = ApiService.getInstance().addContact(otherId);
                if (ok) {
                    //refrescar la lista de contactos
                    loadContacts();
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                Platform.runLater(() -> newContactField.setDisable(false));
            }
        }).start();
    }

    // Inicia o recupera la conversación y la selecciona en la lista de chats
    private void startChatWith(int otherUserId) {
        new Thread(() -> {
            try {
                int convId = ApiService.getInstance().createConversation(otherUserId);
                Platform.runLater(() -> {
                    // Si no está ya en la lista agregarla
                    boolean existe = convoList.getItems().stream()
                            .anyMatch(c -> c.getId() == convId);
                    if (!existe) {
                        Conversation conv = new Conversation(convId, "");
                        convoList.getItems().add(conv);
                    }
                    // Seleccionamos esa conversación
                    convoList.getItems().stream()
                            .filter(c -> c.getId() == convId)
                            .findFirst()
                            .ifPresent(c -> {
                                convoList.getSelectionModel().select(c);
                            });
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void renderMessages(Conversation conv) {
        convoTitle.setText(conv.getTitle());
        // Limpia y vuelve a pintar todas las burbujas según el modelo
        messagesBox.getChildren().clear();
        for (Message m : conv.getMessages()) {
            messagesBox.getChildren().add(MessageBubbleFactory.create(m));
        }

        Platform.runLater(() -> {
            // Mueve la barra hasta abajo para mostrar los ultimos mensajes
            messageScrollPane.setVvalue(1.0);
        });

    }

}
