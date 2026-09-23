package com.example.reactiveorderapi.order.dto;

import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt
) {

    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(
                order.id(),
                order.userId(),
                order.status(),
                order.totalAmount(),
                items.stream().map(OrderItemResponse::from).toList(),
                order.createdAt(),
                order.updatedAt()
        );
    }
}
