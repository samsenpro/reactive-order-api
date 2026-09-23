package com.example.reactiveorderapi.order.repository;

import com.example.reactiveorderapi.order.entity.OrderItem;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;

import java.util.Collection;

public interface OrderItemRepository extends R2dbcRepository<OrderItem, Long> {

    Flux<OrderItem> findAllByOrderIdOrderByProductIdAsc(Long orderId);

    /** Carga los items de una página de pedidos en una única consulta (evita N+1). */
    Flux<OrderItem> findAllByOrderIdInOrderByProductIdAsc(Collection<Long> orderIds);
}
