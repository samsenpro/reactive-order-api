package com.example.reactiveorderapi.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockAdjustmentRequest(
        @Schema(example = "10")
        @NotNull @Positive @Max(MAX_ADJUSTMENT)
        Integer quantity
) {

    /** Límite por operación: evita desbordar la columna INTEGER con un único ajuste. */
    public static final int MAX_ADJUSTMENT = 1_000_000;
}
