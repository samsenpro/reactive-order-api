package com.example.reactiveorderapi.inventory.dto;

import com.example.reactiveorderapi.inventory.entity.Inventory;

import java.time.Instant;

public record InventoryResponse(
        Long productId,
        int availableQuantity,
        int reservedQuantity,
        Instant updatedAt
) {

    public static InventoryResponse from(Inventory inventory) {
        return new InventoryResponse(
                inventory.productId(),
                inventory.availableQuantity(),
                inventory.reservedQuantity(),
                inventory.updatedAt()
        );
    }
}
