package com.example.reactiveorderapi.user.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entidad inmutable mapeada con Spring Data R2DBC (sin JPA). El id nulo indica una
 * entidad nueva; al guardarla, R2DBC devuelve una copia con el id generado.
 */
@Table("users")
public record User(
        @Id Long id,
        String name,
        String email,
        String password,
        Role role,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {

    public static User create(String name, String email, String encodedPassword, Role role, Instant now) {
        return new User(null, name, email, encodedPassword, role, true, now, now);
    }

    @Override
    public String toString() {
        // Nunca incluir el password
        return "User{id=" + id + ", role=" + role + ", enabled=" + enabled + "}";
    }
}
