package com.example.javachat.service;

import com.google.gson.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class ApiService {
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
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        // Codigo del stack over
        InputStream is = code < 400 ? conn.getInputStream() : conn.getErrorStream();
        String responseJson;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            responseJson = reader.lines().collect(Collectors.joining("\n"));
        }

        System.out.println("POST " + path + " → HTTP " + code + "\n" + responseJson);

        if (code >= 400) {
            throw new IOException("HTTP " + code + ": " + responseJson);
        }

        return new Gson().fromJson(responseJson, clazz);
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

    // las demas apis pendientes
}
