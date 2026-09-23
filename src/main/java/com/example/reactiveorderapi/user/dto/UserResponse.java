package com.example.reactiveorderapi.user.dto;

import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;

import java.time.Instant;

/**
 * Representación pública del usuario. Nunca incluye el password.
 */
public record UserResponse(
        Long id,
        String name,
        String email,
        Role role,
        boolean enabled,
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.name(), user.email(), user.role(), user.enabled(), user.createdAt());
    }
}
