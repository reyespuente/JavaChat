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
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
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
import java.util.stream.Collectors;
import java.util.Set;
import java.util.HashSet;
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

    // Grupos
    @FXML private ListView<Conversation> groupList;


    private Conversation currentConv;
    private int currentConversationId = 0;

    private final Map<Integer, String> lastTimestamp = new HashMap<>();

    private final ObservableList<Conversation> masterConvs = FXCollections.observableArrayList();



    @FXML
    public void initialize() {
        // 1) Cell factories
        convoList.setCellFactory(lv -> new ConversationCell());
        groupList.setCellFactory(lv -> {
            ConversationCell cell = new ConversationCell();
            ContextMenu menu = new ContextMenu();

            MenuItem rename = new MenuItem("Renombrar grupo");
            rename.setOnAction(e -> onRenameGroup(cell.getItem()));
            MenuItem addM = new MenuItem("Agregar miembro");
            addM.setOnAction(e -> onAddGroupMember(cell.getItem()));
            MenuItem rmM = new MenuItem("Quitar miembro");
            rmM.setOnAction(e -> onRemoveGroupMember(cell.getItem()));

            menu.getItems().addAll(rename, addM, rmM);
            cell.setContextMenu(menu);
            return cell;
        });
        contactsList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                setText(empty || u == null ? null : u.getDisplayName());
            }
        });

        // 2) Enlaza masterConvs a ambos ListViews usando filtros
        FilteredList<Conversation> directList =
                new FilteredList<>(masterConvs, c -> "direct".equals(c.getType()));
        FilteredList<Conversation> groupListFiltered =
                new FilteredList<>(masterConvs, c -> "group".equals(c.getType()));
        convoList.setItems(directList);
        groupList.setItems(groupListFiltered);

        // 3) Runnable para _actualizar_ masterConvs sin recrear instancias
        Runnable loadConvs = () -> {
            try {
                List<Conversation> fetched = ApiService.getInstance().getConversations();
                Map<Integer,Conversation> map = masterConvs.stream()
                        .collect(Collectors.toMap(Conversation::getId, c -> c));
                Set<Integer> keep = new HashSet<>();
                for (Conversation f : fetched) {
                    keep.add(f.getId());
                    if (map.containsKey(f.getId())) {
                        Conversation exist = map.get(f.getId());
                        exist.titleProperty().set(f.getTitle());
                        exist.setType(f.getType());
                    } else {
                        masterConvs.add(f);
                    }
                }
                masterConvs.removeIf(c -> !keep.contains(c.getId()));
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        };
        new Thread(loadConvs).start();
        Timeline convsRefresher = new Timeline(
                new KeyFrame(Duration.seconds(5), e -> new Thread(loadConvs).start())
        );
        convsRefresher.setCycleCount(Animation.INDEFINITE);
        convsRefresher.play();

        // 4) Listener de selección en chats directos
        convoList.getSelectionModel().selectedItemProperty().addListener((obs, old, conv) -> {
            if (conv != null) {
                currentConversationId = conv.getId();
                convoTitle.setText(conv.getTitle());
                loadConversation(conv);
            }
        });

        // 5) Listener de selección en grupos (sin cambiar pestaña)
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, old, grp) -> {
            if (grp != null) {
                currentConversationId = grp.getId();
                convoTitle.setText(grp.getTitle());
                loadConversation(grp);
            }
        });

        // 6) Polling de nuevos mensajes (incremental)
        Timeline msgRefresher = new Timeline(
                new KeyFrame(Duration.seconds(3), e -> refreshNewMessages())
        );
        msgRefresher.setCycleCount(Animation.INDEFINITE);
        msgRefresher.play();

        // 7) Auto‐scroll cada vez que cambian los mensajes
        messagesBox.heightProperty().addListener((obs, oldH, newH) -> {
            messageScrollPane.setVvalue(1.0);
        });

        // 8) Carga inicial de contactos y perfil
        loadContacts();
        new Thread(() -> {
            try {
                User me = ApiService.getInstance().getProfile();
                Platform.runLater(() -> {
                    profileUsername.setText(me.getUsername());
                    profileFullName.setText(me.getNombreCompleto());
                    profileStatus.setText(me.getMensajeEstado());
                });
            } catch (IOException ignored) {}
        }).start();

        // para mostrar los mensajes no leidos
        convoList.getSelectionModel().selectedItemProperty().addListener((obs, old, conv) -> {
            if (conv != null) {
                currentConversationId = conv.getId();
                convoTitle.setText(conv.getTitle());
                conv.clearUnreadCount();
                loadConversation(conv);
            }
        });
        groupList.getSelectionModel().selectedItemProperty().addListener((obs, old, grp) -> {
            if (grp != null) {
                currentConversationId = grp.getId();
                convoTitle.setText(grp.getTitle());
                grp.clearUnreadCount();
                loadConversation(grp);
            }
        });
    }



    private void loadConversation(Conversation conv) {
        int convId = conv.getId();
        convoTitle.setText(conv.getTitle());
        messagesBox.getChildren().clear();

        new Thread(() -> {
            try {
                List<Message> all = ApiService.getInstance()
                        .getMessages(convId, "1970-01-01 00:00:00");

                Platform.runLater(() -> {
                    conv.getMessages().setAll(all);
                    // en lugar de crear sólo burbujas de texto:
                    for (Message m : all) {
                        // true = primera carga, así no hacemos scroll por cada adjunto
                        addMessageWithAttachments(m, true);
                    }
                    if (!all.isEmpty()) {
                        lastTimestamp.put(convId,
                                all.get(all.size()-1).getSentAt()
                        );
                    }
                    messageScrollPane.setVvalue(1.0);
                });

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }).start();
    }


    private void refreshNewMessages() {
        Conversation sel = convoList.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        int convId = sel.getId();
        String since = lastTimestamp.getOrDefault(convId, "2025-01-01 00:00:00");

        new Thread(() -> {
            try {
                List<Message> nuevos = ApiService.getInstance().getMessages(convId, since);
                if (nuevos.isEmpty()) return;
                String maxTs = nuevos.stream()
                        .map(Message::getSentAt)
                        .max(String::compareTo)
                        .orElse(since);
                Platform.runLater(() -> {
                    // Si esta conversación NO está activa, marcaremos como no leída
                    int selectedId = convoList.getSelectionModel().getSelectedItem().getId();
                    if (selectedId != convId) {
                        // todos los mensajes nuevos son "no leídos"
                        sel.incrementUnreadCount(nuevos.size());
                    }
                    // Igual los agregamos al UI del chat si está abierto
                    for (Message m : nuevos) {
                        sel.addMessage(m);
                        messagesBox.getChildren().add(MessageBubbleFactory.create(m));
                    }
                    lastTimestamp.put(convId, maxTs);
                    // si es la conversación abierta, hacemos scroll
                    if (selectedId == convId) {
                        messageScrollPane.setVvalue(1.0);
                    }
                });
            } catch (IOException ex) {
                ex.printStackTrace();
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
        if (text.isEmpty()) {
            new Alert(Alert.AlertType.WARNING,
                    "No hay mensaje que enviar.",
                    ButtonType.OK).showAndWait();
            return;
        }

        Conversation target;
        Tab sel = tabPane.getSelectionModel().getSelectedItem();
        if ("Grupos".equals(sel.getText())) {
            target = groupList.getSelectionModel().getSelectedItem();
        } else {
            target = convoList.getSelectionModel().getSelectedItem();
        }

        if (target == null) {
            new Alert(Alert.AlertType.WARNING,
                    "No hay ninguna conversación seleccionada.",
                    ButtonType.OK).showAndWait();
            return;
        }

        int convId = target.getId();
        messageField.setDisable(true);

        new Thread(() -> {
            try {
                boolean ok = ApiService.getInstance().sendMessage(convId, text);
                Platform.runLater(() -> {
                    messageField.setDisable(false);
                    if (ok) {
                        messageField.clear();
                        loadConversation(target);
                    } else {
                        System.err.println("Error: sendMessage devolvió false");
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
                Platform.runLater(() -> messageField.setDisable(false));
            }
        }).start();
    }




    // mostrar la informacion del usuario en el boton de info que ya estaba en la ecena del chat
    @FXML
    private void onInfoClicked() {
        // depende la pestaña de chats o de grupos
        Tab selTab = tabPane.getSelectionModel().getSelectedItem();
        Conversation conv;
        if ("Grupos".equals(selTab.getText())) {
            conv = groupList.getSelectionModel().getSelectedItem();
        } else {
            conv = convoList.getSelectionModel().getSelectedItem();
        }
        if (conv == null) return;

        // cargar la info
        new Thread(() -> {
            try {
                List<User> members = ApiService.getInstance().getConversationMembers(conv.getId());
                int me = SessionManager.getInstance().getUserId();
                List<User> others = members.stream()
                        .filter(u -> u.getId() != me)
                        .toList();

                Platform.runLater(() -> {
                    if (others.size() == 1) {
                        showUserInfo(others.get(0));
                    } else {
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


    @FXML
    private void onAttachClicked() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecciona un archivo");
        File file = chooser.showOpenDialog(messageField.getScene().getWindow());
        if (file == null) return;

        new Thread(() -> {
            try {
                int convId = currentConversationId;
                ApiService.getInstance().uploadAttachment(convId, file);
                // recargar en FX
                Platform.runLater(() -> {
                    Conversation sel = convoList.getSelectionModel().getSelectedItem();
                    if (sel != null) loadConversation(sel);
                });
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


    @FXML
    private void onNewGroupClicked() {
        // Diálogo para título
        TextInputDialog titleDlg = new TextInputDialog();
        titleDlg.setTitle("Nuevo Grupo");
        titleDlg.setHeaderText("Nombre del grupo");
        titleDlg.setContentText("Título:");
        titleDlg.showAndWait().ifPresent(title -> {
            if (title.isBlank()) return;
            // Selección múltiple via checklist
            Dialog<List<User>> dlg = new Dialog<>();
            dlg.setTitle("Selecciona miembros");
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            ListView<CheckBox> checklist = new ListView<>();
            for (User u : contactsList.getItems()) {
                CheckBox cb = new CheckBox(u.getDisplayName());
                cb.setUserData(u);
                checklist.getItems().add(cb);
            }
            dlg.getDialogPane().setContent(checklist);
            dlg.setResultConverter(btn -> {
                if (btn == ButtonType.OK) {
                    return checklist.getItems().stream()
                            .filter(CheckBox::isSelected)
                            .map(cb -> (User)cb.getUserData())
                            .collect(Collectors.toList());
                }
                return null;
            });

            dlg.showAndWait().ifPresent(sel -> {
                var ids = sel.stream().map(User::getId).toList();
                if (ids.isEmpty()) return;
                // Crear el grupo en un nuevo hilo
                new Thread(() -> {
                    try {
                        int newId = ApiService.getInstance().createGroup(title, ids);
                        Platform.runLater(() -> {
                            Conversation c = new Conversation(newId, title);
                            c.setType("group");
                            groupList.getItems().add(c);
                            groupList.getSelectionModel().select(c);
                        });
                    } catch(IOException ex) {
                        ex.printStackTrace();
                        Platform.runLater(() ->
                                new Alert(Alert.AlertType.ERROR,
                                        "Error al crear grupo",
                                        ButtonType.OK).showAndWait()
                        );
                    }
                }).start();
            });
        });
    }


    private void onRenameGroup(Conversation grp) {
        if (grp==null) return;
        TextInputDialog dlg = new TextInputDialog(grp.getTitle());
        dlg.setTitle("Renombrar grupo");
        dlg.setHeaderText("Nuevo nombre para \"" + grp.getTitle() + "\"");
        dlg.setContentText("Título:");
        dlg.showAndWait().ifPresent(newTitle -> {
            if (newTitle.isBlank()) return;
            new Thread(() -> {
                try {
                    boolean ok = ApiService.getInstance().renameGroup(grp.getId(), newTitle);
                    if (ok) {
                        Platform.runLater(() -> {
                            grp.titleProperty().set(newTitle);
                            // si está activo en el panel, también actualizamos el título
                            if (currentConversationId == grp.getId()) {
                                convoTitle.setText(newTitle);
                            }
                        });
                    }
                } catch (IOException ex) {
                    ex.printStackTrace();
                    Platform.runLater(() ->
                            new Alert(Alert.AlertType.ERROR, "No se pudo renombrar el grupo").showAndWait()
                    );
                }
            }).start();
        });
    }

    private void onAddGroupMember(Conversation group) {
        if (group == null) return;

        new Thread(() -> {
            try {
                List<User> currentMembers = ApiService.getInstance().getConversationMembers(group.getId());
                Set<Integer> currentMemberIds = currentMembers.stream()
                        .map(User::getId)
                        .collect(Collectors.toSet());

                List<User> availableContacts = contactsList.getItems().stream()
                        .filter(contact -> !currentMemberIds.contains(contact.getId()))
                        .collect(Collectors.toList());

                if (availableContacts.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Información");
                        alert.setHeaderText(null);
                        alert.setContentText("Todos tus contactos ya están en el grupo");
                        alert.showAndWait();
                    });
                    return;
                }

                Platform.runLater(() -> {
                    Dialog<List<User>> dialog = new Dialog<>();
                    dialog.setTitle("Agregar miembros a " + group.getTitle());

                    ListView<User> selectionList = new ListView<>(FXCollections.observableArrayList(availableContacts));
                    selectionList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                    dialog.getDialogPane().setContent(selectionList);
                    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                    dialog.setResultConverter(buttonType -> {
                        if (buttonType == ButtonType.OK) {
                            return selectionList.getSelectionModel().getSelectedItems();
                        }
                        return null;
                    });

                    dialog.showAndWait().ifPresent(selectedUsers -> {
                        if (!selectedUsers.isEmpty()) {
                            new Thread(() -> {
                                for (User user : selectedUsers) {
                                    try {
                                        boolean success = ApiService.getInstance()
                                                .addGroupMember(group.getId(), user.getId());

                                        if (success) {
                                            Platform.runLater(() -> {
                                                Alert info = new Alert(Alert.AlertType.INFORMATION);
                                                info.setTitle("Éxito");
                                                info.setHeaderText(null);
                                                info.setContentText(user.getDisplayName() + " ha sido agregado al grupo");
                                                info.showAndWait();
                                            });
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        }
                    });
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void onRemoveGroupMember(Conversation group) {
        if (group == null) return;

        new Thread(() -> {
            try {
                int currentUserId = SessionManager.getInstance().getUserId();
                List<User> members = ApiService.getInstance().getConversationMembers(group.getId())
                        .stream()
                        .filter(u -> u.getId() != currentUserId)
                        .collect(Collectors.toList());

                if (members.isEmpty()) {
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.INFORMATION);
                        alert.setTitle("Información");
                        alert.setHeaderText(null);
                        alert.setContentText("No hay miembros para eliminar");
                        alert.showAndWait();
                    });
                    return;
                }

                Platform.runLater(() -> {
                    Dialog<List<User>> dialog = new Dialog<>();
                    dialog.setTitle("Eliminar miembros de " + group.getTitle());

                    ListView<User> memberListView = new ListView<>(FXCollections.observableArrayList(members));
                    memberListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                    dialog.getDialogPane().setContent(memberListView);
                    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

                    dialog.setResultConverter(buttonType -> {
                        if (buttonType == ButtonType.OK) {
                            return memberListView.getSelectionModel().getSelectedItems();
                        }
                        return null;
                    });

                    dialog.showAndWait().ifPresent(selectedUsers -> {
                        if (!selectedUsers.isEmpty()) {
                            new Thread(() -> {
                                for (User user : selectedUsers) {
                                    try {
                                        boolean success = ApiService.getInstance()
                                                .removeGroupMember(group.getId(), user.getId());

                                        if (success) {
                                            Platform.runLater(() -> {
                                                Alert info = new Alert(Alert.AlertType.INFORMATION);
                                                info.setTitle("Éxito");
                                                info.setHeaderText(null);
                                                info.setContentText(user.getDisplayName() + " ha sido eliminado del grupo");
                                                info.showAndWait();
                                            });
                                        }
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }).start();
                        }
                    });
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();
    }

}
