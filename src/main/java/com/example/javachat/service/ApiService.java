package com.example.javachat.service;

import com.example.javachat.model.Conversation;
import com.example.javachat.model.User;
import com.example.javachat.session.SessionManager;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import com.example.javachat.model.Message;
import java.io.IOException;
import java.lang.reflect.Type;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


import static java.nio.charset.StandardCharsets.UTF_8;


public class ApiService {

    /// //////////////////////////////////////////////
    public static boolean debug = true;
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
        int    id;
        String title;
        String type;
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

        return dtos.stream().map(d -> {
            Conversation c = new Conversation(d.id, d.title);
            c.setType(d.type);
            return c;
        }).collect(Collectors.toList());
    }

    // obtener mensajes de la conversacion
    public List<Message> getMessages(int conversationId, String since) throws IOException {
        String query = String.format("?conversation_id=%d&since=%s",
                conversationId,
                URLEncoder.encode(since, StandardCharsets.UTF_8)
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



    public List<User> listContacts() throws IOException {
        String json = get("/listContacts.php");
        Type t = new TypeToken<List<ContactDTO>>(){}.getType();
        List<ContactDTO> dtos = new Gson().fromJson(json, t);
        return dtos.stream()
                .map(d -> new User(
                        d.id,
                        d.username,
                        d.nombre_completo,
                        d.mensaje_estado       // corregido :v
                ))
                .collect(Collectors.toList());
    }

    // Solicita agregar contacto
    public boolean addContact(int contactId) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("contact_id", contactId);
        StatusResponse resp = post("/addContact.php", body, StatusResponse.class);
        return "ok".equals(resp.status);
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
        int    id;
        String username;
        String nombre_completo;
        String mensaje_estado;
    }

    private static class StatusResponse { String status; }

    private static class MemberDTO {
        int    id;
        String username;
        String nombre_completo;
        String mensaje_estado;
    }

    public List<User> getConversationMembers(int conversationId) throws IOException {
        String json = get("/listMembers.php?conversation_id=" + conversationId);
        Type listType = new TypeToken<List<MemberDTO>>(){}.getType();
        List<MemberDTO> dtos = new Gson().fromJson(json, listType);

        return dtos.stream()
                .map(d -> new User(
                        d.id,
                        d.username,
                        d.nombre_completo,
                        d.mensaje_estado
                ))
                .collect(Collectors.toList());
    }

    // actualizar perfil
    public boolean updateProfile(String username,
                                 String fullName,
                                 String statusMessage,
                                 String newPassword,
                                 String confirmPassword) throws IOException {
        JsonObject p = new JsonObject();
        p.addProperty("username", username);
        p.addProperty("nombre_completo", fullName);
        p.addProperty("mensaje_estado", statusMessage);

        // solo actualizar la cotnra cuando no estan en blanco las celdas
        if (newPassword != null && !newPassword.isBlank()) {
            p.addProperty("new_password", newPassword);
            p.addProperty("confirm_password", confirmPassword);
        }

        StatusResponse resp = post("/updateProfile.php", p, StatusResponse.class);
        return "ok".equals(resp.status);
    }


    // DTO para mi propio perfil
    private static class ProfileDTO {
        int    id;
        String username;
        String nombre_completo;
        String mensaje_estado;
    }

    // solo devuelve mi perfil de la DB
    public User getProfile() throws IOException {
        String json = get("/getProfile.php");
        ProfileDTO dto = new Gson().fromJson(json, ProfileDTO.class);
        return new User(
                dto.id,
                dto.username,
                dto.nombre_completo,
                dto.mensaje_estado
        );
    }

    // DTO para adjunto
    public static class Attachment {
        public int    id;
        public int    mensaje_id;
        public String file_url;
        public String file_type;
        public int    file_size;
        public String original_name;
        public String subido_en;
    }

    // Listar adjuntos de una conversación
    public List<Attachment> getAttachments(int conversationId) throws IOException {
        String json = get("/getAttachments.php?conversation_id=" + conversationId);
        Type listType = new TypeToken<List<Attachment>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }

    // sube un adjunto usando multipart/form-data
    public boolean uploadAttachment(int conversationId, File f) throws IOException {
        String boundary = Long.toHexString(System.currentTimeMillis());
        URL url = new URL(baseUrl + "/sendAttachment.php");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization","Bearer "+token);
        conn.setRequestProperty("Content-Type","multipart/form-data; boundary="+boundary);

        try(var out = new DataOutputStream(conn.getOutputStream())) {
            // campo conversation_id
            out.writeBytes("--"+boundary+"\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"conversation_id\"\r\n\r\n");
            out.writeBytes(String.valueOf(conversationId)+"\r\n");

            // campo file
            out.writeBytes("--"+boundary+"\r\n");
            out.writeBytes(
                    "Content-Disposition: form-data; name=\"file\"; filename=\""+f.getName()+"\"\r\n"
            );
            out.writeBytes("Content-Type: "+Files.probeContentType(f.toPath())+"\r\n\r\n");
            Files.copy(f.toPath(), out);
            out.writeBytes("\r\n");
            // campo file_size
            out.writeBytes("--"+boundary+"--\r\n");
        }

        int code = conn.getResponseCode();
        if (code != 200) throw new IOException("Upload failed HTTP "+code);
        return true;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public List<Attachment> getMessageAttachments(int messageId) throws IOException {
        String json = get("/getMessageAttachments.php?message_id=" + messageId);
        Type listType = new TypeToken<List<Attachment>>(){}.getType();
        return new Gson().fromJson(json, listType);
    }


    private static class CreateGroupResp {
        public int conversation_id;
    }

    // Crea un grupo con título y lista de IDs de miembros devuelve el conversation_id creado.
    public int createGroup(String title, List<Integer> memberIds) throws IOException {
        JsonObject p = new JsonObject();
        p.addProperty("title", title);
        JsonArray arr = new JsonArray();
        memberIds.forEach(arr::add);
        p.add("members", arr);

        CreateGroupResp resp = post("/createGroup.php", p, CreateGroupResp.class);
        return resp.conversation_id;
    }

    // Agrega un miembro a un grupo existente
    public boolean addGroupMember(int convId, int userId) throws IOException {
        JsonObject p = new JsonObject();
        p.addProperty("conversation_id", convId);
        p.addProperty("user_id", userId);
        StatusResponse r = post("/addGroupMember.php", p, StatusResponse.class);
        return "ok".equals(r.status);
    }

    // eliminar un miembro
    public boolean removeGroupMember(int convId, int userId) throws IOException {
        JsonObject p = new JsonObject();
        p.addProperty("conversation_id", convId);
        p.addProperty("user_id", userId);
        StatusResponse r = post("/removeGroupMember.php", p, StatusResponse.class);
        return "ok".equals(r.status);
    }

    // renombrar grupo
    public boolean renameGroup(int convId, String newTitle) throws IOException {
        JsonObject p = new JsonObject();
        p.addProperty("conversation_id", convId);
        p.addProperty("title", newTitle);
        StatusResponse r = post("/renameGroup.php", p, StatusResponse.class);
        return "ok".equals(r.status);
    }

}
