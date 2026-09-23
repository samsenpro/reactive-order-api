package com.example.reactiveorderapi.inventory.repository;

import com.example.reactiveorderapi.inventory.entity.Inventory;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

/**
 * Todas las escrituras de stock son UPDATE condicionales y atómicos: la comprobación y la
 * modificación ocurren en una única sentencia, con el bloqueo de fila de PostgreSQL. Devuelven
 * el número de filas afectadas; 0 significa que la condición no se cumplió (stock insuficiente
 * o producto inexistente).
 */
public interface InventoryRepository extends R2dbcRepository<Inventory, Long> {

    Mono<Inventory> findByProductId(Long productId);

    Flux<Inventory> findAllByProductIdIn(Collection<Long> productIds);

    /** Pasa unidades de disponibles a reservadas solo si hay suficientes disponibles. */
    @Modifying
    @Query("""
            UPDATE inventory
               SET available_quantity = available_quantity - :quantity,
                   reserved_quantity  = reserved_quantity + :quantity,
                   updated_at         = now()
             WHERE product_id = :productId
               AND available_quantity >= :quantity
            """)
    Mono<Long> reserve(Long productId, int quantity);

    /** Devuelve unidades reservadas a disponibles (pedido cancelado o eliminado). */
    @Modifying
    @Query("""
            UPDATE inventory
               SET available_quantity = available_quantity + :quantity,
                   reserved_quantity  = reserved_quantity - :quantity,
                   updated_at         = now()
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """)
    Mono<Long> release(Long productId, int quantity);

    /** Descuenta definitivamente las unidades reservadas (pedido completado). */
    @Modifying
    @Query("""
            UPDATE inventory
               SET reserved_quantity = reserved_quantity - :quantity,
                   updated_at        = now()
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """)
    Mono<Long> consumeReserved(Long productId, int quantity);

    @Modifying
    @Query("""
            UPDATE inventory
               SET available_quantity = available_quantity + :quantity,
                   updated_at         = now()
             WHERE product_id = :productId
            """)
    Mono<Long> increaseAvailable(Long productId, int quantity);

    @Modifying
    @Query("""
            UPDATE inventory
               SET available_quantity = available_quantity - :quantity,
                   updated_at         = now()
             WHERE product_id = :productId
               AND available_quantity >= :quantity
            """)
    Mono<Long> decreaseAvailable(Long productId, int quantity);
}
