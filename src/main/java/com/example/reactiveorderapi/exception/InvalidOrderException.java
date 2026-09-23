package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El pedido viola una regla de negocio (HTTP 422).
 */
public class InvalidOrderException extends ApiException {

    public InvalidOrderException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
