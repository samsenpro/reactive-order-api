package com.example.reactiveorderapi.security;

import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado que viaja en el {@code SecurityContext} reactivo. Inmutable.
 */
public record AuthenticatedUser(
        Long id,
        String email,
        String passwordHash,
        Role role,
        boolean enabled
) implements UserDetails {

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.id(), user.email(), user.password(), user.role(), user.enabled());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.authority()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", role=" + role + ", enabled=" + enabled + "}";
    }
}
