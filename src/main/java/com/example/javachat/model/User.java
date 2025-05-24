package com.example.javachat.model;

/**
 * Modelo simple para un usuario/contacto, con estado.
 */
public class User {
    private final int    id;
    private final String username;
    private final String nombreCompleto;
    private final String mensajeEstado;   // <- nuevo campo

    public User(int id, String username, String nombreCompleto, String mensajeEstado) {
        this.id               = id;
        this.username         = username;
        this.nombreCompleto   = nombreCompleto;
        this.mensajeEstado    = mensajeEstado;
    }

    public int getId() {
        return id;
    }
    public String getUsername() {
        return username;
    }
    public String getNombreCompleto() {
        return nombreCompleto;
    }
    public String getMensajeEstado() {
        return mensajeEstado;
    }

    // Para mostrar en listas
    public String getDisplayName() {
        return (nombreCompleto != null && !nombreCompleto.isBlank())
                ? nombreCompleto
                : username;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
