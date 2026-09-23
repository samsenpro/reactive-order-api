package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Conflicto con un recurso existente, p. ej. email o SKU repetido (HTTP 409).
 */
public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
