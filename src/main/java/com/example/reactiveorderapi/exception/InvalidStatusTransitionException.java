package com.example.reactiveorderapi.exception;

import com.example.reactiveorderapi.order.entity.OrderStatus;

/**
 * Transición de estado no permitida por la máquina de estados del pedido (HTTP 422).
 */
public class InvalidStatusTransitionException extends InvalidOrderException {

    public InvalidStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot change order status from " + from + " to " + to);
    }
}
