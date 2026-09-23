package com.example.reactiveorderapi.order.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Table("orders")
public record Order(
        @Id Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        Instant updatedAt
) {

    public static Order pending(Long userId, BigDecimal totalAmount, Instant now) {
        return new Order(null, userId, OrderStatus.PENDING, totalAmount, now, now);
    }

    public boolean isOwnedBy(Long candidateUserId) {
        return userId.equals(candidateUserId);
    }
}
