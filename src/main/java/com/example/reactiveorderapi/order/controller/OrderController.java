package com.example.reactiveorderapi.order.controller;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.common.PageResponse;
import com.example.reactiveorderapi.order.dto.CreateOrderRequest;
import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.order.dto.UpdateOrderStatusRequest;
import com.example.reactiveorderapi.order.service.OrderCreationService;
import com.example.reactiveorderapi.order.service.OrderService;
import com.example.reactiveorderapi.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Orders")
public class OrderController {

    private final OrderCreationService orderCreationService;
    private final OrderService orderService;
    private final CurrentUser currentUser;

    public OrderController(OrderCreationService orderCreationService,
                           OrderService orderService,
                           CurrentUser currentUser) {
        this.orderCreationService = orderCreationService;
        this.orderService = orderService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Operation(summary = "Crear un pedido: valida productos y stock, calcula precios en backend y reserva stock")
    public Mono<ResponseEntity<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        return currentUser.get()
                .flatMap(user -> orderCreationService.create(user.id(), request))
                .map(order -> ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order));
    }

    @GetMapping
    @Operation(summary = "Listar pedidos: los propios (USER) o todos (ADMIN, filtrable por userId)")
    public Mono<PageResponse<OrderResponse>> findPage(
            @Parameter(description = "Solo ADMIN: filtrar por usuario") @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = PageQuery.DEFAULT_PAGE) @Min(0) int page,
            @RequestParam(defaultValue = PageQuery.DEFAULT_SIZE) @Min(1) @Max(PageQuery.MAX_SIZE) int size) {
        return currentUser.get()
                .flatMap(user -> orderService.findPage(user, userId, new PageQuery(page, size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un pedido (propio, o cualquiera si es ADMIN)")
    public Mono<OrderResponse> findById(@PathVariable Long id) {
        return currentUser.get().flatMap(user -> orderService.findById(user, id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Cambiar el estado. USER solo puede cancelar sus pedidos PENDING o CONFIRMED")
    public Mono<OrderResponse> changeStatus(@PathVariable Long id,
                                            @Valid @RequestBody UpdateOrderStatusRequest request) {
        return currentUser.get().flatMap(user -> orderService.changeStatus(user, id, request.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eliminar un pedido PENDING (libera su stock) o CANCELLED")
    public Mono<Void> delete(@PathVariable Long id) {
        return currentUser.get().flatMap(user -> orderService.delete(user, id));
    }
}
