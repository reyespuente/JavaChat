package com.example.javachat.service;

import com.example.javachat.model.Conversation;
import com.example.javachat.model.User;
import com.example.javachat.session.SessionManager;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.stream.Collectors;
import com.example.javachat.model.Message;
import java.io.IOException;
import java.lang.reflect.Type;

import static java.nio.charset.StandardCharsets.UTF_8;


public class ApiService {

    /// //////////////////////////////////////////////
    boolean debug = true;
    /// /////////////////////////////////////////////

    private final String baseUrl = "https://api.reyespuente.com";
    private String token;

    // Singleton para consumir la api
    private static final ApiService INSTANCE = new ApiService();
    private ApiService(){}
    public static ApiService getInstance(){ return INSTANCE; }

    public void setToken(String token){ this.token = token; }

    // Genérico para parsear respuesta de la api JWT ej: {"status":"ok","user_id":x}
    private <T> T post(String path, JsonObject body, Class<T> clazz) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type","application/json");
        if (token != null) conn.setRequestProperty("Authorization","Bearer " + token);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.toString().getBytes(UTF_8));
        }

        int code = conn.getResponseCode();
        // Codigo del stack over
        InputStream is;
        if (code < 400) is = conn.getInputStream();
        else is = conn.getErrorStream();
        String responseJson;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            responseJson = reader.lines().collect(Collectors.joining("\n"));
        }

        if (debug){
            System.out.println("POST " + path + " → HTTP " + code + "\n" + responseJson);
        }


        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + responseJson);
        }

        return new Gson().fromJson(responseJson, clazz);
    }


    // --- Respuesta de login ---
    public static class LoginResponse {
        public String token;
        public int user_id;
    }

   //  Hace login en /login.php, guarda el JWT y userId en SessionManager
    public boolean loginUser(String username, String password) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);

        LoginResponse resp = post("/login.php", payload, LoginResponse.class);
        if (resp.token != null && !resp.token.isEmpty()) {
            this.token = resp.token;
            // Guarda en sesión global
            SessionManager.getInstance().setToken(resp.token);
            SessionManager.getInstance().setUserId(resp.user_id);

            if (debug){
                System.out.println("JWT obtenido: " + resp.token);
            }

            return true;
        }
        return false;
    }



    /** DTO para registro */
    public static class RegisterResponse { public String status; public int user_id; }

    public int registerUser(String username, String password, String fullName) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);
        payload.addProperty("nombre_completo", fullName);

        RegisterResponse resp = post("/register.php", payload, RegisterResponse.class);
        return "ok".equals(resp.status) ? resp.user_id : -1;
    }


    /** DTO para la respuesta de createConversation */
    private static class CreateConvResponse {
        public int conversation_id;
    }

    /**
     * Crea o recupera una conversación directa entre el usuario actual y otro.
     * @param otherUserId  ID del otro usuario (contacto)
     * @return el ID de la conversación
     */
    public int createConversation(int otherUserId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("with", otherUserId);
        CreateConvResponse resp = post("/createConversation.php", payload, CreateConvResponse.class);
        return resp.conversation_id;
    }


    /** DTO para listar conversaciones */
    private static class ConversationDTO {
        int id;
        String title;
    }

    /** DTO para listar mensajes */
    private static class MessageDTO {
        int id;
        int sender_id;
        String content;
        String sent_at;
    }

    // Convierte y devuelve la lista de conversaciones
    public List<Conversation> getConversations() throws IOException {
        String json = get("/listConversations.php");
        Type listType = new TypeToken<List<ConversationDTO>>(){}.getType();
        List<ConversationDTO> dtos = new Gson().fromJson(json, listType);

        return dtos.stream()
                .map(d -> new Conversation(d.id, d.title))
                .collect(Collectors.toList());
    }

    // obtener mensajes de la conversacion
    public List<Message> getMessages(int conversationId, String since) throws IOException {
        String query = String.format("?conversation_id=%d&since=%s",
                conversationId,
                URLEncoder.encode(since, UTF_8)
        );
        String json = get("/getMessages.php" + query);
        Type listType = new TypeToken<List<MessageDTO>>(){}.getType();
        List<MessageDTO> dtos = new Gson().fromJson(json, listType);

        return dtos.stream()
                .map(m -> new Message(m.id, m.sender_id, m.content, m.sent_at))
                .collect(Collectors.toList());
    }



    private String get(String path) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + token);
        conn.setRequestProperty("Accept", "application/json");

        int code = conn.getResponseCode();

        // Intenta primero el stream de error, si no existe usa el de input
        InputStream errorStream = conn.getErrorStream();
        InputStream inputStream = conn.getInputStream();
        InputStream is;
        if (code < 400) {
            is = inputStream != null ? inputStream : errorStream;
        } else {
            is = errorStream != null ? errorStream : inputStream;
        }

        if (is == null) {
            // Ni error ni input stream -> error sin cuerpo
            throw new IOException("HTTP " + code + ": sin respuesta del servidor");
        }

        String body;
        try (var rd = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            body = rd.lines().collect(Collectors.joining("\n"));
        }

        if (debug) {
            System.out.println("GET " + path + " → HTTP " + code + "\n" + body);
        }

        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + body);
        }
        return body;
    }

    public boolean sendMessage(int conversationId, String content) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("conversation_id", conversationId);
        payload.addProperty("content", content);
        SendResponse resp = post("/sendMessage.php", payload, SendResponse.class);
        return "ok".equals(resp.status);
    }

    // DTO interno
    private static class SendResponse {
        public String status;
    }



    // Devuelve lista de contactos
    public List<User> listContacts() throws IOException {
        String json = get("/listContacts.php");
        Type t = new TypeToken<List<ContactDTO>>(){}.getType();
        List<ContactDTO> dtos = new Gson().fromJson(json, t);
        return dtos.stream()
                .map(d -> new User(d.id, d.username, d.nombre))
                .collect(Collectors.toList());
    }

    // Solicita agregar contacto
    public boolean addContact(int contactId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("contact_id", contactId);
        StatusResponse resp = post("/addContact.php", body, StatusResponse.class);
        return "pending".equals(resp.status);
    }

    // Obtiene ID de usuario a partir de su username
    public int getUserIdByUsername(String username) throws IOException {
        String json = get("/getUserByUsername.php?username=" +
                URLEncoder.encode(username, UTF_8));
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        return obj.get("user_id").getAsInt();
    }

    // DTOs Contacto
    private static class ContactDTO {
        int id;
        String username;
        String nombre;
    }
    private static class StatusResponse { String status; }

}
