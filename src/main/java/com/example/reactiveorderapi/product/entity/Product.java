package com.example.reactiveorderapi.product.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("products")
public record Product(
        @Id Long id,
        String name,
        String description,
        String sku,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {

    public static Product create(String name, String description, String sku, BigDecimal price, Instant now) {
        return new Product(null, name, description, sku, price, true, now, now);
    }

    public Product withDetails(String name, String description, String sku, BigDecimal price, Instant now) {
        return new Product(id, name, description, sku, price, active, createdAt, now);
    }

    public Product withActive(boolean active, Instant now) {
        return new Product(id, name, description, sku, price, active, createdAt, now);
    }
}
