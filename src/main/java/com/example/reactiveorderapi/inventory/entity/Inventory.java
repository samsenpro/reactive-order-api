package com.example.reactiveorderapi.inventory.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Stock de un producto (relación 1:1 con products).
 * <ul>
 *     <li>{@code availableQuantity}: unidades que se pueden vender.</li>
 *     <li>{@code reservedQuantity}: unidades comprometidas en pedidos aún no completados.</li>
 * </ul>
 * Las modificaciones se hacen siempre con UPDATE atómicos en {@code InventoryRepository},
 * nunca leyendo, modificando en memoria y guardando.
 */
@Table("inventory")
public record Inventory(
        @Id Long id,
        Long productId,
        int availableQuantity,
        int reservedQuantity,
        Instant updatedAt
) {

    public static Inventory create(Long productId, int availableQuantity, Instant now) {
        return new Inventory(null, productId, availableQuantity, 0, now);
    }

    public boolean canFulfil(int quantity) {
        return availableQuantity >= quantity;
    }
}
