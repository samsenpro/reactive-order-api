package com.example.reactiveorderapi.product.dto;

import com.example.reactiveorderapi.product.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id(),
                product.name(),
                product.description(),
                product.sku(),
                product.price(),
                product.active(),
                product.createdAt(),
                product.updatedAt()
        );
    }
}
