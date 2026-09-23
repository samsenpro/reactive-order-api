package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El estado del pedido cambió de forma concurrente durante la operación (HTTP 409).
 */
public class OrderConflictException extends ApiException {

    public OrderConflictException(Long orderId) {
        super(HttpStatus.CONFLICT, "Order " + orderId + " was modified concurrently; reload it and try again");
    }
}
