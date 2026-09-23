package com.example.reactiveorderapi.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Línea solicitada por el cliente: solo producto y cantidad. El precio nunca viene del cliente.
 */
public record OrderItemRequest(
        @Schema(example = "1")
        @NotNull @Positive
        Long productId,

        @Schema(example = "2")
        @NotNull @Positive @Max(MAX_QUANTITY)
        Integer quantity
) {

    public static final int MAX_QUANTITY = 1_000;
}
