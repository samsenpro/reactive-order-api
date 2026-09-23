package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

/**
 * No hay stock disponible suficiente para la cantidad solicitada (HTTP 409).
 */
public class InsufficientStockException extends ApiException {

    private final Long productId;
    private final int requestedQuantity;

    public InsufficientStockException(Long productId, int requestedQuantity) {
        super(HttpStatus.CONFLICT,
                "Insufficient stock for product " + productId + " (requested " + requestedQuantity + ")");
        this.productId = productId;
        this.requestedQuantity = requestedQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }
}
