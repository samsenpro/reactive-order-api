package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.exception.InvalidOrderException;
import com.example.reactiveorderapi.order.dto.CreateOrderRequest;
import com.example.reactiveorderapi.order.dto.OrderItemRequest;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Cantidades pedidas por producto, validadas y consolidadas.
 * <ul>
 *     <li>Las líneas repetidas del mismo producto se suman.</li>
 *     <li>Se ordenan por {@code productId}: las reservas de stock se hacen siempre en el mismo
 *     orden y así dos pedidos concurrentes no se bloquean mutuamente (deadlock).</li>
 * </ul>
 * Se valida aquí además de con Bean Validation: el servicio no depende de que la capa web lo haya hecho.
 */
public record RequestedItems(SortedMap<Long, Integer> quantities) {

    public RequestedItems {
        quantities = Collections.unmodifiableSortedMap(new TreeMap<>(quantities));
    }

    public static RequestedItems from(CreateOrderRequest request) {
        List<OrderItemRequest> items = request == null ? null : request.items();
        if (items == null || items.isEmpty()) {
            throw new InvalidOrderException("Order must contain at least one item");
        }
        if (items.size() > CreateOrderRequest.MAX_ITEMS) {
            throw new InvalidOrderException("Order cannot contain more than " + CreateOrderRequest.MAX_ITEMS + " items");
        }

        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (OrderItemRequest item : items) {
            validate(item);
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        quantities.forEach((productId, quantity) -> {
            if (quantity > OrderItemRequest.MAX_QUANTITY) {
                throw new InvalidOrderException("Quantity for product " + productId
                        + " exceeds the maximum of " + OrderItemRequest.MAX_QUANTITY);
            }
        });
        return new RequestedItems(quantities);
    }

    public Set<Long> productIds() {
        return quantities.keySet();
    }

    private static void validate(OrderItemRequest item) {
        if (item == null || item.productId() == null || item.productId() <= 0) {
            throw new InvalidOrderException("Every item needs a valid productId");
        }
        if (item.quantity() == null || item.quantity() <= 0) {
            throw new InvalidOrderException("Quantity must be positive for product " + item.productId());
        }
    }
}
