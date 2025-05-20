package com.example.javachat.model;

/**
 * Modelo simple para un usuario/contacto.
 */
public class User {
    private final int    id;
    private final String username;
    private final String nombreCompleto;

    public User(int id, String username, String nombreCompleto) {
        this.id             = id;
        this.username       = username;
        this.nombreCompleto = nombreCompleto;
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

    // mostrar el nombre de usuario, si en la basde de datos es NULL o blanco solo muesta el username
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
