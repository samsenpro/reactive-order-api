package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.exception.InsufficientStockException;
import com.example.reactiveorderapi.exception.ProductInactiveException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.order.service.OrderPricing.PricedLine;
import com.example.reactiveorderapi.order.service.OrderPricing.PricedOrder;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static com.example.reactiveorderapi.support.Fixtures.inventory;
import static com.example.reactiveorderapi.support.Fixtures.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPricingTest {

    @Test
    void usesBackendPricesAndComputesSubtotalsAndTotal() {
        RequestedItems requested = new RequestedItems(new TreeMap<>(Map.of(2L, 1, 1L, 3)));

        PricedOrder priced = OrderPricing.price(requested,
                Map.of(1L, product(1L, "19.99", true), 2L, product(2L, "0.05", true)),
                Map.of(1L, inventory(1L, 10, 0), 2L, inventory(2L, 10, 0)));

        assertThat(priced.lines()).extracting(PricedLine::productId).containsExactly(1L, 2L);
        assertThat(priced.lines().getFirst().subtotal()).isEqualByComparingTo("59.97");
        assertThat(priced.totalAmount()).isEqualByComparingTo("60.02").hasScaleOf(2);
    }

    @Test
    void missingProductIsRejected() {
        RequestedItems requested = new RequestedItems(new TreeMap<>(Map.of(1L, 1)));

        assertThatThrownBy(() -> OrderPricing.price(requested, Map.of(), Map.of()))
                .isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void inactiveProductIsRejected() {
        RequestedItems requested = new RequestedItems(new TreeMap<>(Map.of(1L, 1)));

        assertThatThrownBy(() -> OrderPricing.price(requested,
                Map.of(1L, product(1L, "1.00", false)), Map.of(1L, inventory(1L, 10, 0))))
                .isInstanceOf(ProductInactiveException.class);
    }

    @Test
    void insufficientOrMissingInventoryIsRejected() {
        RequestedItems requested = new RequestedItems(new TreeMap<>(Map.of(1L, 5)));

        assertThatThrownBy(() -> OrderPricing.price(requested,
                Map.of(1L, product(1L, "1.00", true)), Map.of(1L, inventory(1L, 4, 0))))
                .isInstanceOf(InsufficientStockException.class);
        assertThatThrownBy(() -> OrderPricing.price(requested,
                Map.of(1L, product(1L, "1.00", true)), Map.of()))
                .isInstanceOf(InsufficientStockException.class);
    }
}
