package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El pedido no existe o no pertenece al usuario (HTTP 404).
 */
public class OrderNotFoundException extends ApiException {

    public OrderNotFoundException(Long orderId) {
        super(HttpStatus.NOT_FOUND, "Order not found: " + orderId);
    }
}
