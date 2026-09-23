package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * El producto existe pero está desactivado y no se puede vender (HTTP 422).
 */
public class ProductInactiveException extends ApiException {

    public ProductInactiveException(Long productId) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Product is not active: " + productId);
    }
}
