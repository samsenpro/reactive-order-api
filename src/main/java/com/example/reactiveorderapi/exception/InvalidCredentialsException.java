package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Credenciales incorrectas. Mensaje genérico para no permitir la enumeración de usuarios (HTTP 401).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
