package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Parámetros de la petición incoherentes (HTTP 400).
 */
public class BadRequestException extends ApiException {

    public BadRequestException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
