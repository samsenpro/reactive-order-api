package com.example.reactiveorderapi.order.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * Máquina de estados del pedido.
 * <pre>
 * PENDING ──► CONFIRMED ──► PROCESSING ──► COMPLETED
 *    │            │              │
 *    └────────────┴──────────────┴──► CANCELLED
 * </pre>
 * Efecto sobre el stock: al cancelar se liberan las reservas; al completar se consumen.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PROCESSING,
    COMPLETED,
    CANCELLED;

    public Set<OrderStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> EnumSet.of(PROCESSING, CANCELLED);
            case PROCESSING -> EnumSet.of(COMPLETED, CANCELLED);
            case COMPLETED, CANCELLED -> EnumSet.noneOf(OrderStatus.class);
        };
    }

    public boolean canTransitionTo(OrderStatus target) {
        return allowedTransitions().contains(target);
    }

    /** Mientras el pedido no ha salido del almacén, su stock sigue reservado. */
    public boolean holdsReservedStock() {
        return this == PENDING || this == CONFIRMED || this == PROCESSING;
    }
}
