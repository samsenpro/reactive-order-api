package com.example.reactiveorderapi.inventory.service;

import com.example.reactiveorderapi.exception.InsufficientStockException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.inventory.entity.Inventory;
import com.example.reactiveorderapi.inventory.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Collection;

/**
 * Operaciones de stock. Cada escritura es un UPDATE condicional: si afecta 0 filas se
 * averigua el motivo (producto inexistente o stock insuficiente) y se emite el error de negocio.
 */
@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final InventoryRepository inventoryRepository;
    private final Clock clock;

    public InventoryService(InventoryRepository inventoryRepository, Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.clock = clock;
    }

    public Mono<Inventory> createFor(Long productId, int initialStock) {
        return inventoryRepository.save(Inventory.create(productId, initialStock, clock.instant()));
    }

    public Mono<Inventory> findByProductId(Long productId) {
        return inventoryRepository.findByProductId(productId)
                .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(productId)));
    }

    public Flux<Inventory> findAllByProductIds(Collection<Long> productIds) {
        return inventoryRepository.findAllByProductIdIn(productIds);
    }

    public Mono<Inventory> addStock(Long productId, int quantity) {
        return inventoryRepository.increaseAvailable(productId, quantity)
                .flatMap(updated -> updated > 0
                        ? findByProductId(productId)
                        : Mono.error(new ProductNotFoundException(productId)))
                .doOnNext(inventory -> log.info("Stock added productId={} quantity={} available={}",
                        productId, quantity, inventory.availableQuantity()));
    }

    public Mono<Inventory> removeStock(Long productId, int quantity) {
        return inventoryRepository.decreaseAvailable(productId, quantity)
                .flatMap(updated -> updated > 0
                        ? findByProductId(productId)
                        : explainFailedUpdate(productId, quantity))
                .doOnNext(inventory -> log.info("Stock removed productId={} quantity={} available={}",
                        productId, quantity, inventory.availableQuantity()));
    }

    /**
     * Reserva stock para un pedido. Seguro ante concurrencia: la condición
     * {@code available_quantity >= quantity} se evalúa y aplica atómicamente en PostgreSQL.
     */
    public Mono<Void> reserve(Long productId, int quantity, Long orderId) {
        return inventoryRepository.reserve(productId, quantity)
                .flatMap(updated -> updated > 0
                        ? Mono.<Void>empty()
                        : Mono.error(new InsufficientStockException(productId, quantity)))
                .doOnSuccess(ignored -> log.info("Stock reserved orderId={} productId={} quantity={}",
                        orderId, productId, quantity));
    }

    public Mono<Void> release(Long productId, int quantity, Long orderId) {
        return inventoryRepository.release(productId, quantity)
                .flatMap(updated -> ensureUpdated(updated, productId))
                .doOnSuccess(ignored -> log.info("Stock released orderId={} productId={} quantity={}",
                        orderId, productId, quantity));
    }

    public Mono<Void> consumeReserved(Long productId, int quantity, Long orderId) {
        return inventoryRepository.consumeReserved(productId, quantity)
                .flatMap(updated -> ensureUpdated(updated, productId))
                .doOnSuccess(ignored -> log.info("Reserved stock consumed orderId={} productId={} quantity={}",
                        orderId, productId, quantity));
    }

    private Mono<Inventory> explainFailedUpdate(Long productId, int quantity) {
        return findByProductId(productId)
                .flatMap(existing -> Mono.error(new InsufficientStockException(productId, quantity)));
    }

    /**
     * Liberar o consumir reservas solo falla si el inventario se corrompió (las reservas
     * del pedido ya no están). Se aborta la transacción en lugar de dejar datos incoherentes.
     */
    private Mono<Void> ensureUpdated(long updated, Long productId) {
        return updated > 0
                ? Mono.empty()
                : Mono.error(new IllegalStateException("Reserved stock inconsistent for product " + productId));
    }
}
