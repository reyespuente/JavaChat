package com.example.javachat.session;

public class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    private String token;
    private int userId;

    private SessionManager() {}

    public static SessionManager getInstance() {
        return INSTANCE;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
}
