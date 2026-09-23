package com.example.reactiveorderapi.product.dto;

import com.example.reactiveorderapi.inventory.dto.StockAdjustmentRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Alta de producto. El inventario se crea en la misma transacción con {@code initialStock}.
 */
public record CreateProductRequest(
        @Schema(example = "Mechanical Keyboard")
        @NotBlank @Size(max = 150)
        String name,

        @Schema(example = "Teclado mecánico con switches rojos")
        @Size(max = 2000)
        String description,

        @Schema(example = "KB-001", description = "Letras, números y guiones; se guarda en mayúsculas")
        @NotBlank
        @Pattern(regexp = ProductRequest.SKU_REGEX, message = "must contain 3-64 letters, digits, '-' or '_'")
        String sku,

        @Schema(example = "89.90")
        @NotNull
        @DecimalMin(value = "0.01", message = "must be greater than 0")
        @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Schema(example = "25", description = "Stock inicial; 0 si se omite")
        @PositiveOrZero @Max(StockAdjustmentRequest.MAX_ADJUSTMENT)
        Integer initialStock
) {

    public int initialStockOrZero() {
        return initialStock == null ? 0 : initialStock;
    }

    public ProductRequest details() {
        return new ProductRequest(name, description, sku, price);
    }
}
