package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.exception.InvalidOrderException;
import com.example.reactiveorderapi.order.dto.CreateOrderRequest;
import com.example.reactiveorderapi.order.dto.OrderItemRequest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestedItemsTest {

    @Test
    void mergesRepeatedProductsAndSortsByProductId() {
        RequestedItems items = RequestedItems.from(new CreateOrderRequest(List.of(
                new OrderItemRequest(5L, 1),
                new OrderItemRequest(2L, 2),
                new OrderItemRequest(5L, 3))));

        assertThat(items.quantities()).containsExactly(
                org.assertj.core.api.Assertions.entry(2L, 2),
                org.assertj.core.api.Assertions.entry(5L, 4));
    }

    @Test
    void rejectsEmptyOrNullItems() {
        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(List.of())))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(null)))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> RequestedItems.from(null))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void rejectsInvalidLines() {
        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(List.of(new OrderItemRequest(null, 1)))))
                .isInstanceOf(InvalidOrderException.class);
        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(List.of(new OrderItemRequest(1L, 0)))))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void rejectsMergedQuantityAboveLimit() {
        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(List.of(
                new OrderItemRequest(1L, OrderItemRequest.MAX_QUANTITY),
                new OrderItemRequest(1L, 1)))))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("exceeds the maximum");
    }

    @Test
    void rejectsTooManyItems() {
        List<OrderItemRequest> tooMany = Collections.nCopies(CreateOrderRequest.MAX_ITEMS + 1, new OrderItemRequest(1L, 1));

        assertThatThrownBy(() -> RequestedItems.from(new CreateOrderRequest(tooMany)))
                .isInstanceOf(InvalidOrderException.class);
    }
}
