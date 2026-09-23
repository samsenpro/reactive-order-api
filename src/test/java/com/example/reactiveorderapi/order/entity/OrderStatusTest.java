package com.example.reactiveorderapi.order.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING,CONFIRMED,true",
            "PENDING,CANCELLED,true",
            "PENDING,COMPLETED,false",
            "CONFIRMED,PROCESSING,true",
            "CONFIRMED,PENDING,false",
            "PROCESSING,COMPLETED,true",
            "PROCESSING,CANCELLED,true",
            "COMPLETED,CANCELLED,false",
            "CANCELLED,PENDING,false"
    })
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }

    @Test
    void onlyOpenOrdersHoldReservedStock() {
        assertThat(OrderStatus.PENDING.holdsReservedStock()).isTrue();
        assertThat(OrderStatus.PROCESSING.holdsReservedStock()).isTrue();
        assertThat(OrderStatus.COMPLETED.holdsReservedStock()).isFalse();
        assertThat(OrderStatus.CANCELLED.holdsReservedStock()).isFalse();
    }
}
