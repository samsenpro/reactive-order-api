package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El producto tiene pedidos asociados y no se puede borrar; debe desactivarse (HTTP 409).
 */
public class ProductInUseException extends ApiException {

    public ProductInUseException(Long productId) {
        super(HttpStatus.CONFLICT, "Product " + productId + " has orders and cannot be deleted; deactivate it instead");
    }
}
