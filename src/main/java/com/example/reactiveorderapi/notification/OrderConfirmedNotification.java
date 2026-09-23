package com.example.reactiveorderapi.notification;

import com.example.reactiveorderapi.order.dto.OrderResponse;

import java.math.BigDecimal;

/**
 * Evento que se envía al servicio externo cuando un pedido pasa a CONFIRMED.
 */
public record OrderConfirmedNotification(
        String type,
        Long orderId,
        Long userId,
        BigDecimal totalAmount
) {

    public static final String TYPE = "ORDER_CONFIRMED";

    public static OrderConfirmedNotification from(OrderResponse order) {
        return new OrderConfirmedNotification(TYPE, order.id(), order.userId(), order.totalAmount());
    }
}
