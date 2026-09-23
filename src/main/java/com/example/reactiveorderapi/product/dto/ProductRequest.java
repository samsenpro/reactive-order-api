package com.example.reactiveorderapi.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Datos de un producto (alta y actualización completa).
 */
public record ProductRequest(
        @Schema(example = "Mechanical Keyboard")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(example = "Teclado mecánico con switches rojos")
        @Size(max = 2000)
        String description,

        @Schema(example = "KB-001", description = "Letras, números y guiones; se guarda en mayúsculas")
        @NotBlank
        @Pattern(regexp = SKU_REGEX, message = "must contain 3-64 letters, digits, '-' or '_'")
        String sku,

        @Schema(example = "89.90")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be greater than 0")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price
) {

    public static final String SKU_REGEX = "^[A-Za-z0-9_-]{3,64}$";
}
