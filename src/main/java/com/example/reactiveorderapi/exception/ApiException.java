package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Base de las excepciones de negocio. Cada subclase declara el código HTTP con el que
 * se traduce en {@link GlobalErrorWebExceptionHandler}.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;

    protected ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
