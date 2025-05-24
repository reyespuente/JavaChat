package com.example.javachat.session;

import java.util.Base64;
import java.util.prefs.Preferences;

public class SessionManager {
    private static final SessionManager INSTANCE = new SessionManager();
    private static final Preferences PREFS =
            Preferences.userRoot().node("JavaChat");

    private String token;
    private int userId;

    private SessionManager(){}

    public static SessionManager getInstance(){ return INSTANCE; }

    public void setToken(String t){
        this.token = t;
        PREFS.put("token", t);
        // extrae exp del JWT y guarda la fecha (epoch ms)
        long exp = parseExpiration(t);
        PREFS.putLong("expiry", exp);
    }
    public String getToken(){ return token; }

    public void setUserId(int id){
        this.userId = id;
        PREFS.putInt("userId", id);
    }
    public int getUserId(){ return userId; }

    // Borra de memoria y de Preferences
    public void clear() {
        token = null;
        userId = 0;
        PREFS.remove("token");
        PREFS.remove("expiry");
        PREFS.remove("userId");
    }

    // Intenta cargar la sesión desde Preferences al inicio
    public boolean loadFromPrefs() {
        String t = PREFS.get("token", null);
        long exp = PREFS.getLong("expiry", 0);
        int uid = PREFS.getInt("userId", 0);
        if (t != null && uid != 0 && System.currentTimeMillis() < exp) {
            this.token = t;
            this.userId = uid;
            return true;
        }
        return false;
    }

    // Extrae el tiempo de expiración del JWT
    private long parseExpiration(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            String body = new String(Base64.getUrlDecoder().decode(parts[1]));
            int i = body.indexOf("\"exp\":");
            if (i>0) {
                String sub = body.substring(i+6);
                long expSec = Long.parseLong(sub.split("[,}]")[0]);
                return expSec * 1000;
            }
        } catch(Exception ignored){}
        return 0;
    }
}
