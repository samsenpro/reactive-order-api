package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.inventory.entity.Inventory;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.order.dto.CreateOrderRequest;
import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.order.repository.OrderItemRepository;
import com.example.reactiveorderapi.order.repository.OrderRepository;
import com.example.reactiveorderapi.order.service.OrderPricing.PricedOrder;
import com.example.reactiveorderapi.product.entity.Product;
import com.example.reactiveorderapi.product.service.ProductService;
import com.example.reactiveorderapi.user.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.List;

/**
 * Flujo principal de la API: crear un pedido.
 * <pre>
 * validar items ─► usuario activo ─► productos ∥ inventario ─► precios y total ─┐
 *                                                                              ▼
 *                         [ transacción: order ─► items ─► reserva de stock ] ─► respuesta
 * </pre>
 * Solo las escrituras van dentro de la transacción; las lecturas previas usan conexiones
 * del pool en paralelo y no alargan la transacción.
 */
@Service
public class OrderCreationService {

    private static final Logger log = LoggerFactory.getLogger(OrderCreationService.class);

    private final UserService userService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final TransactionalOperator transactionalOperator;
    private final Clock clock;

    public OrderCreationService(UserService userService,
                                ProductService productService,
                                InventoryService inventoryService,
                                OrderRepository orderRepository,
                                OrderItemRepository orderItemRepository,
                                TransactionalOperator transactionalOperator,
                                Clock clock) {
        this.userService = userService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.transactionalOperator = transactionalOperator;
        this.clock = clock;
    }

    public Mono<OrderResponse> create(Long userId, CreateOrderRequest request) {
        return Mono.fromCallable(() -> RequestedItems.from(request))
                .delayUntil(items -> userService.requireActiveUser(userId))
                .flatMap(this::priceOrder)
                .flatMap(priced -> persist(userId, priced))
                .doOnNext(order -> log.info("Order created orderId={} userId={} total={} items={}",
                        order.id(), userId, order.totalAmount(), order.items().size()));
    }

    /**
     * Productos e inventario son independientes: {@code Mono.zip} lanza ambas consultas a la
     * vez y continúa cuando las dos han terminado. Cada una es una sola consulta {@code IN (...)}.
     */
    private Mono<PricedOrder> priceOrder(RequestedItems items) {
        return Mono.zip(
                        productService.findAllByIds(items.productIds()).collectMap(Product::id),
                        inventoryService.findAllByProductIds(items.productIds()).collectMap(Inventory::productId))
                .map(catalog -> OrderPricing.price(items, catalog.getT1(), catalog.getT2()));
    }

    /**
     * Pedido, items y reserva de stock son atómicos. Si cualquier reserva falla (por ejemplo,
     * porque otro pedido se llevó el stock entre la lectura y la escritura), la señal de error
     * llega al {@link TransactionalOperator}, que hace ROLLBACK de todo lo anterior.
     */
    private Mono<OrderResponse> persist(Long userId, PricedOrder priced) {
        return orderRepository.save(Order.pending(userId, priced.totalAmount(), clock.instant()))
                .zipWhen(order -> saveItems(order.id(), priced))
                .delayUntil(saved -> reserveStock(saved.getT1().id(), priced))
                .map(saved -> OrderResponse.from(saved.getT1(), saved.getT2()))
                .as(transactionalOperator::transactional);
    }

    private Mono<List<OrderItem>> saveItems(Long orderId, PricedOrder priced) {
        return orderItemRepository
                .saveAll(Flux.fromIterable(priced.lines()).map(line -> line.toItem(orderId)))
                .collectList();
    }

    /**
     * {@code concatMap}: una reserva detrás de otra, en orden de productId. Dentro de una
     * transacción todas las sentencias comparten una única conexión, y el orden fijo de los
     * bloqueos de fila evita deadlocks entre pedidos concurrentes.
     */
    private Mono<Void> reserveStock(Long orderId, PricedOrder priced) {
        return Flux.fromIterable(priced.lines())
                .concatMap(line -> inventoryService.reserve(line.productId(), line.quantity(), orderId))
                .then();
    }
}
