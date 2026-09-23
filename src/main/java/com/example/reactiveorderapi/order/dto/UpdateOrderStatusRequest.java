package com.example.reactiveorderapi.order.dto;

import com.example.reactiveorderapi.order.entity.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(
        @Schema(example = "CONFIRMED")
        @NotNull
        OrderStatus status
) {
}
