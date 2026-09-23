package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El usuario no existe o está deshabilitado (HTTP 404).
 */
public class UserNotFoundException extends ApiException {

    public UserNotFoundException(Long userId) {
        super(HttpStatus.NOT_FOUND, "User not found: " + userId);
    }
}
