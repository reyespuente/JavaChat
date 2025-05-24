package com.example.javachat.controller;
import com.example.javachat.model.Conversation;
import com.example.javachat.model.Message;
import com.example.javachat.model.User;
import com.example.javachat.service.ApiService;
import com.example.javachat.session.SessionManager;
import com.example.javachat.ui.ConversationCell;
import com.example.javachat.ui.MessageBubbleFactory;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.example.javachat.service.ApiService.Attachment;
import javafx.scene.Node;
import javafx.scene.Cursor;
import javafx.scene.control.Hyperlink;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

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

    // para editar el perfil
    @FXML private TextField    profileUsername;
    @FXML private TextField    profileFullName;
    @FXML private TextField    profileStatus;
    @FXML private PasswordField profileNewPassword;
    @FXML private PasswordField profileConfirmPass;
    @FXML private Label         profileMsgLabel;



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
                startChatWith(u);
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

        // nuevo hilo para obtener mi perfil en los tabs del chat.fxml
        new Thread(() -> {
            try {
                User me = ApiService.getInstance().getProfile();
                Platform.runLater(() -> {
                    profileUsername.setText(me.getUsername());
                    profileFullName.setText(me.getNombreCompleto());
                    profileStatus.setText(me.getMensajeEstado());
                });
            } catch (IOException ignored) {
            }
        }).start();


    }



    private void loadConversation(Conversation conv) {
        int convId = conv.getId();
        convoTitle.setText(conv.getTitle());

        boolean firstLoad = !lastTimestamp.containsKey(convId);
        // Si es la primera vez repasamos TODO el historial
        if (firstLoad) {
            messagesBox.getChildren().clear();
            // pintamos historial completo
            for (Message m : conv.getMessages()) {
                addMessageWithAttachments(m, firstLoad);
            }
        }

        String since = lastTimestamp.getOrDefault(convId, "2025-01-01 00:00:00");

        new Thread(() -> {
            try {
                // Traer solo mensajes nuevos
                List<Message> nuevos = ApiService.getInstance()
                        .getMessages(convId, since);
                // Determinar el timestamp más reciente
                String maxTs = since;
                for (Message m : nuevos) {
                    if (m.getSentAt().compareTo(maxTs) > 0) {
                        maxTs = m.getSentAt();
                    }
                    // Guardar en el modelo
                    conv.addMessage(m);
                }

                final String newSince = maxTs;
                Platform.runLater(() -> {
                    // Añadir cada burbuja + sus adjuntos
                    for (Message m : nuevos) {
                        addMessageWithAttachments(m, false);
                    }
                    // Actualizar para la proxima
                    lastTimestamp.put(convId, newSince);
                    // Auto‐scroll
                    messageScrollPane.setVvalue(1.0);
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Helper para convertir un Attachment en un Node (imagen o enlace)
    private Node createAttachmentNode(Attachment a) {
        // Construye la URL completa
        String base = ApiService.getInstance().getBaseUrl();
        String url  = base + a.file_url;

        if (a.file_type.startsWith("image/")) {
            // Si es imagen se pone la miniatura en la burbija del chat
            ImageView img = new ImageView(new Image(url, 120, 0, true, true));
            img.setCursor(Cursor.HAND);
            img.setOnMouseClicked(e -> {
                try {
                    Desktop.getDesktop().browse(new URI(url));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            });
            return img;
        } else {
            // mostramos el nombre del archivo como un enlace
            String label = a.original_name != null
                    ? a.original_name
                    : a.file_url.substring(a.file_url.lastIndexOf('/')+1);
            Hyperlink link = new Hyperlink(label);
            link.setOnAction(e -> {
                try { Desktop.getDesktop().browse(new URI(url)); }
                catch (Exception ex) { ex.printStackTrace(); }
            });
            return link;
        }
    }



    @FXML
    private void onSendClicked() {
        String text = messageField.getText().trim();
        if (text.isEmpty() || currentConversationId == 0) {
            // ahora si mostrarlo en pantalla :v
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Atención");
            alert.setHeaderText(null);
            alert.setContentText("No hay mensaje que enviar o no ha seleccionado una conversación.");
            alert.showAndWait();
            return;
        }

        // ERROR CORREGIDO DE DOBLEMENSAJE
        messageField.setDisable(true);
        if (ApiService.debug) {
            System.out.println("Enviando mensaje: \"" + text + "\" a conv " + currentConversationId);
        }

        new Thread(() -> {
            try {
                boolean ok = ApiService.getInstance().sendMessage(currentConversationId, text);
                if (ApiService.debug) {
                    System.out.println("sendMessage returned: " + ok);
                }
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


    // mostrar la informacion del usuario en el boton de info que ya estaba en la ecena del chat
    @FXML
    private void onInfoClicked() {
        Conversation conv = convoList.getSelectionModel().getSelectedItem();
        if (conv == null) return;

        new Thread(() -> {
            try {
                List<User> members = ApiService.getInstance().getConversationMembers(conv.getId());
                // NO MOSTRAR MI MISMA INFOOOOOO
                int me = SessionManager.getInstance().getUserId();
                List<User> others = members.stream()
                        .filter(u -> u.getId() != me)
                        .toList();

                Platform.runLater(() -> {
                    if (others.size() == 1) {
                        // mostrar la info
                        showUserInfo(others.get(0));
                    } else {
                        // grupo como es grupo hay q seleccionar a cual le vamos a ver la info
                        ChoiceDialog<User> dlg = new ChoiceDialog<>(others.get(0), others);
                        dlg.setTitle("Participantes de “" + conv.getTitle() + "”");
                        dlg.setHeaderText("Selecciona un usuario");
                        dlg.setContentText("Usuario:");
                        dlg.showAndWait().ifPresent(this::showUserInfo);
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR,
                                "No se pudo cargar la información del chat",
                                ButtonType.OK)
                                .showAndWait()
                );
            }
        }).start();
    }

    //la info
    private void showUserInfo(User user) {
        Alert info = new Alert(Alert.AlertType.INFORMATION);
        info.setTitle("Perfil de " + user.getDisplayName());
        info.setHeaderText(null);

        String content =
                "Username: "     + user.getUsername()       + "\n" +
                        "Nombre: "       + (user.getNombreCompleto() == null
                        ? "—"
                        : user.getNombreCompleto()) + "\n" +
                        "Estado: "       + (user.getMensajeEstado() == null
                        ? "—"
                        : user.getMensajeEstado());

        info.setContentText(content);
        info.showAndWait();
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
    private void startChatWith(User user) {
        new Thread(() -> {
            try {
                int convId = ApiService.getInstance().createConversation(user.getId());
                Platform.runLater(() -> {
                    // 1 Si no existe, lo agregamos con el nombre del user
                    boolean existe = convoList.getItems().stream()
                            .anyMatch(c -> c.getId() == convId);
                    if (!existe) {
                        // usa displayName para que aparezca el nombre correcto
                        Conversation conv = new Conversation(convId, user.getDisplayName());
                        convoList.getItems().add(conv);
                    }
                    // 2 selecciona la conversación creada
                    convoList.getItems().stream()
                            .filter(c -> c.getId() == convId)
                            .findFirst()
                            .ifPresent(c -> convoList.getSelectionModel().select(c));
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

    // accion al dar clic en el boton de gaurdal del chat.fxml
    @FXML
    private void onProfileSaveClicked() {
        String u = profileUsername.getText().trim();
        String n = profileFullName.getText().trim();
        String s = profileStatus.getText().trim();
        String p = profileNewPassword.getText();
        String c = profileConfirmPass.getText();

        // Mostrar luego luego un mensaje de guardando
        profileMsgLabel.setTextFill(Color.GRAY);
        profileMsgLabel.setText("Guardando…");

        new Thread(() -> {
            try {
                boolean ok = ApiService.getInstance()
                        .updateProfile(u, n, s, p, c);
                Platform.runLater(() -> {
                    if (ok) {
                        profileMsgLabel.setTextFill(Color.web("#4CAF50"));
                        profileMsgLabel.setText("Perfil actualizado correctamente.");
                        // al refrescar solo limpiar los campos de la contra
                        profileNewPassword.clear();
                        profileConfirmPass.clear();
                    } else {
                        profileMsgLabel.setTextFill(Color.RED);
                        profileMsgLabel.setText("Error al guardar perfil.");
                    }
                });
            } catch (IOException e) {
                Platform.runLater(() -> {
                    profileMsgLabel.setTextFill(Color.RED);
                    profileMsgLabel.setText("Fallo de red o servidor.");
                });
            }
        }).start();
    }


    @FXML private void onAttachClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona un archivo");
        File file = chooser.showOpenDialog(messageField.getScene().getWindow());
        if (file == null) return;
        // sube en un hilo
        new Thread(() -> {
            try {
                int convId = currentConversationId;
                ApiService.getInstance().uploadAttachment(convId, file);
                // recargar adjuntos y mensajes tras subirlo
                loadConversation(convoList.getSelectionModel().getSelectedItem());
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() ->
                        new Alert(Alert.AlertType.ERROR,
                                "No se pudo subir el archivo", ButtonType.OK)
                                .showAndWait()
                );
            }
        }).start();
    }

    // los adjuntos se añaden a la burbuja del mensaje
    private void addMessageWithAttachments(Message m, boolean wasFirstLoad) {
        // se crea la burbuja de mensaje
        HBox bubbleContainer = (HBox) MessageBubbleFactory.create(m);
        //    dentro va el VBox con texto + timestamp
        @SuppressWarnings("unchecked")
        VBox bubble = (VBox) bubbleContainer.getChildren().get(0);

        // ajduntos: si el mensaje tiene adjuntos, los cargamos
        new Thread(() -> {
            try {
                List<Attachment> atchs = ApiService.getInstance()
                        .getMessageAttachments(m.getId());
                Platform.runLater(() -> {
                    for (Attachment a : atchs) {
                        Node attachNode = createAttachmentNode(a);
                        // lo metemos dentro de la burbuja
                        bubble.getChildren().add(1, attachNode);
                        // el índice 1 es justo después del texto, antes del timestamp
                    }
                    // si no es primera carga, hacemos scroll tras un pequeño delay:
                    if (!wasFirstLoad) {
                        Platform.runLater(() -> messageScrollPane.setVvalue(1.0));
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();

        // se añade la burbuja (con adjuntos dentro) a la vista
        messagesBox.getChildren().add(bubbleContainer);
    }

    @FXML
    private void onLogoutClicked() {
        SessionManager.getInstance().clear();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/javachat/fxml/Login.fxml")
            );
            Stage stage = (Stage) convoList.getScene().getWindow();
            stage.setScene(new Scene(loader.load()));
            stage.setTitle("JavaChat – Login");
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
