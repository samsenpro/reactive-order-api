package com.example.reactiveorderapi.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty
        @Size(max = MAX_ITEMS)
        List<@NotNull @Valid OrderItemRequest> items
) {

    public static final int MAX_ITEMS = 50;
}
