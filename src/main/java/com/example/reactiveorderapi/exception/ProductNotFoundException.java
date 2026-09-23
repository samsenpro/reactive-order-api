package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El producto no existe (HTTP 404).
 */
public class ProductNotFoundException extends ApiException {

    public ProductNotFoundException(Long productId) {
        super(HttpStatus.NOT_FOUND, "Product not found: " + productId);
    }
}
