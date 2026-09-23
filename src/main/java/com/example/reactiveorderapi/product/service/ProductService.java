package com.example.reactiveorderapi.product.service;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.common.PageResponse;
import com.example.reactiveorderapi.exception.BadRequestException;
import com.example.reactiveorderapi.exception.DuplicateResourceException;
import com.example.reactiveorderapi.exception.ProductInUseException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.product.dto.CreateProductRequest;
import com.example.reactiveorderapi.product.dto.ProductRequest;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.product.dto.ProductSearchCriteria;
import com.example.reactiveorderapi.product.entity.Product;
import com.example.reactiveorderapi.product.repository.ProductRepository;
import com.example.reactiveorderapi.product.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.Collection;
import java.util.Locale;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /**
     * Filas que el stream pide a la BD en cada lote. {@code limitRate} convierte la demanda
     * del cliente HTTP en peticiones acotadas al driver R2DBC (backpressure extremo a extremo).
     */
    static final int STREAM_BATCH_SIZE = 50;

    private final ProductRepository productRepository;
    private final ProductSearchRepository productSearchRepository;
    private final InventoryService inventoryService;
    private final TransactionalOperator transactionalOperator;
    private final Clock clock;

    public ProductService(ProductRepository productRepository,
                          ProductSearchRepository productSearchRepository,
                          InventoryService inventoryService,
                          TransactionalOperator transactionalOperator,
                          Clock clock) {
        this.productRepository = productRepository;
        this.productSearchRepository = productSearchRepository;
        this.inventoryService = inventoryService;
        this.transactionalOperator = transactionalOperator;
        this.clock = clock;
    }

    /** Crea el producto y su inventario en una única transacción reactiva. */
    public Mono<ProductResponse> create(CreateProductRequest request) {
        String sku = normalizeSku(request.sku());
        return ensureSkuAvailable(productRepository.existsBySku(sku), sku)
                .then(Mono.defer(() -> productRepository.save(Product.create(
                        request.name().trim(), request.description(), sku, request.price(), clock.instant()))))
                .flatMap(product -> inventoryService.createFor(product.id(), request.initialStockOrZero())
                        .thenReturn(product))
                .as(transactionalOperator::transactional)
                .onErrorMap(DuplicateKeyException.class, ex -> skuTaken(sku))
                .doOnNext(product -> log.info("Product created productId={} sku={}", product.id(), sku))
                .map(ProductResponse::from);
    }

    public Mono<ProductResponse> findById(Long id) {
        return findProduct(id).map(ProductResponse::from);
    }

    /** Carga varios productos en una sola consulta {@code IN (...)}; los inexistentes no se emiten. */
    public Flux<Product> findAllByIds(Collection<Long> ids) {
        return productRepository.findAllById(ids);
    }

    /**
     * Página filtrada. Contenido y total son consultas independientes, así que se lanzan en
     * paralelo con {@code Mono.zip} y se combinan cuando ambas terminan.
     */
    public Mono<PageResponse<ProductResponse>> search(ProductSearchCriteria criteria, PageQuery page) {
        if (criteria.hasInvalidPriceRange()) {
            return Mono.error(new BadRequestException("minPrice must be less than or equal to maxPrice"));
        }
        return Mono.zip(
                        productSearchRepository.search(criteria, page).map(ProductResponse::from).collectList(),
                        productSearchRepository.count(criteria))
                .map(result -> PageResponse.of(result.getT1(), page, result.getT2()));
    }

    /** Productos activos como flujo continuo; ver {@link #STREAM_BATCH_SIZE}. */
    public Flux<ProductResponse> streamActiveProducts() {
        return productRepository.findAllByActiveTrueOrderByIdAsc()
                .limitRate(STREAM_BATCH_SIZE)
                .map(ProductResponse::from);
    }

    public Mono<ProductResponse> update(Long id, ProductRequest request) {
        String sku = normalizeSku(request.sku());
        return findProduct(id)
                .delayUntil(existing -> ensureSkuAvailable(productRepository.existsBySkuAndIdNot(sku, id), sku))
                .map(existing -> existing.withDetails(
                        request.name().trim(), request.description(), sku, request.price(), clock.instant()))
                .flatMap(productRepository::save)
                .onErrorMap(DuplicateKeyException.class, ex -> skuTaken(sku))
                .map(ProductResponse::from);
    }

    public Mono<ProductResponse> activate(Long id) {
        return changeActive(id, true);
    }

    public Mono<ProductResponse> deactivate(Long id) {
        return changeActive(id, false);
    }

    /**
     * Borra el producto y, en cascada, su inventario. Si tiene pedidos, la FK (RESTRICT)
     * lo impide y se informa de que debe desactivarse.
     */
    public Mono<Void> delete(Long id) {
        return findProduct(id)
                .flatMap(productRepository::delete)
                .onErrorMap(DataIntegrityViolationException.class, ex -> new ProductInUseException(id))
                .doOnSuccess(ignored -> log.info("Product deleted productId={}", id));
    }

    private Mono<ProductResponse> changeActive(Long id, boolean active) {
        return findProduct(id)
                .map(product -> product.withActive(active, clock.instant()))
                .flatMap(productRepository::save)
                .doOnNext(product -> log.info("Product productId={} active={}", id, active))
                .map(ProductResponse::from);
    }

    private Mono<Product> findProduct(Long id) {
        return productRepository.findById(id)
                .switchIfEmpty(Mono.error(() -> new ProductNotFoundException(id)));
    }

    private Mono<Void> ensureSkuAvailable(Mono<Boolean> skuExists, String sku) {
        return skuExists.flatMap(exists -> exists ? Mono.error(skuTaken(sku)) : Mono.empty());
    }

    private static DuplicateResourceException skuTaken(String sku) {
        return new DuplicateResourceException("SKU already exists: " + sku);
    }

    private static String normalizeSku(String sku) {
        return sku.trim().toUpperCase(Locale.ROOT);
    }
}
