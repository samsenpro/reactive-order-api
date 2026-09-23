package com.example.reactiveorderapi.order.repository;

import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface OrderRepository extends R2dbcRepository<Order, Long> {

    Mono<Order> findByIdAndUserId(Long id, Long userId);

    Flux<Order> findAllByUserId(Long userId, Pageable pageable);

    Mono<Long> countByUserId(Long userId);

    Flux<Order> findAllBy(Pageable pageable);

    /**
     * Cambio de estado con control de concurrencia optimista: solo se aplica si el pedido
     * sigue en el estado leído. Evita, por ejemplo, que dos cancelaciones simultáneas
     * liberen el stock dos veces.
     */
    @Modifying
    @Query("""
            UPDATE orders
               SET status = :target, updated_at = now()
             WHERE id = :orderId
               AND status = :expected
            """)
    Mono<Long> updateStatus(Long orderId, String expected, String target);

    @Modifying
    @Query("DELETE FROM orders WHERE id = :orderId AND status = :expected")
    Mono<Long> deleteIfStatus(Long orderId, String expected);
}
