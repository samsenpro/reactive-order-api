package com.example.reactiveorderapi.order.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("order_items")
public record OrderItem(
        @Id Long id,
        Long orderId,
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

    public static OrderItem of(Long orderId, Long productId, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
        return new OrderItem(null, orderId, productId, quantity, unitPrice, subtotal);
    }
}
