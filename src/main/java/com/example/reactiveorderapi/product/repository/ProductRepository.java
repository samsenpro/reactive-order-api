package com.example.reactiveorderapi.product.repository;

import com.example.reactiveorderapi.product.entity.Product;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ProductRepository extends R2dbcRepository<Product, Long> {

    Mono<Boolean> existsBySku(String sku);

    Mono<Boolean> existsBySkuAndIdNot(String sku, Long id);

    /** Origen del endpoint de streaming: la BD emite filas según la demanda del consumidor. */
    Flux<Product> findAllByActiveTrueOrderByIdAsc();
}
