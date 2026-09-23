package com.example.reactiveorderapi.order.dto;

import com.example.reactiveorderapi.order.entity.OrderItem;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.productId(), item.quantity(), item.unitPrice(), item.subtotal());
    }
}
