package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.common.PageResponse;
import com.example.reactiveorderapi.exception.InvalidOrderException;
import com.example.reactiveorderapi.exception.InvalidStatusTransitionException;
import com.example.reactiveorderapi.exception.OrderConflictException;
import com.example.reactiveorderapi.exception.OrderNotFoundException;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.notification.NotificationClient;
import com.example.reactiveorderapi.notification.OrderConfirmedNotification;
import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import com.example.reactiveorderapi.order.repository.OrderItemRepository;
import com.example.reactiveorderapi.order.repository.OrderRepository;
import com.example.reactiveorderapi.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Consulta, cambio de estado y borrado de pedidos. La creación está en {@link OrderCreationService}.
 * <p>
 * Control de acceso: un USER solo ve sus pedidos (uno ajeno responde 404, como si no existiera)
 * y solo puede cancelarlos mientras no estén en preparación. El resto de transiciones son de ADMIN.
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<OrderStatus> USER_CANCELLABLE = EnumSet.of(OrderStatus.PENDING, OrderStatus.CONFIRMED);
    private static final Set<OrderStatus> DELETABLE = EnumSet.of(OrderStatus.PENDING, OrderStatus.CANCELLED);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryService;
    private final NotificationClient notificationClient;
    private final TransactionalOperator transactionalOperator;

    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        InventoryService inventoryService,
                        NotificationClient notificationClient,
                        TransactionalOperator transactionalOperator) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.inventoryService = inventoryService;
        this.notificationClient = notificationClient;
        this.transactionalOperator = transactionalOperator;
    }

    // --- Consultas ---

    public Mono<OrderResponse> findById(AuthenticatedUser user, Long orderId) {
        return findAccessibleOrder(user, orderId).flatMap(this::withItems);
    }

    /**
     * USER: sus pedidos. ADMIN: todos, o los de {@code userIdFilter} si se indica.
     * La página y el total se consultan en paralelo; los items de toda la página, en una sola consulta.
     */
    public Mono<PageResponse<OrderResponse>> findPage(AuthenticatedUser user, Long userIdFilter, PageQuery page) {
        Long ownerId = user.isAdmin() ? userIdFilter : user.id();
        Flux<Order> orders = ownerId == null
                ? orderRepository.findAllBy(page.toPageable())
                : orderRepository.findAllByUserId(ownerId, page.toPageable());
        Mono<Long> total = ownerId == null ? orderRepository.count() : orderRepository.countByUserId(ownerId);

        return Mono.zip(orders.collectList(), total)
                .flatMap(result -> withItems(result.getT1())
                        .map(content -> PageResponse.of(content, page, result.getT2())));
    }

    // --- Cambio de estado ---

    /**
     * Aplica la transición y su efecto sobre el stock en una transacción. Después, fuera de la
     * transacción, notifica la confirmación; un fallo de la notificación no deshace el pedido.
     */
    public Mono<OrderResponse> changeStatus(AuthenticatedUser user, Long orderId, OrderStatus target) {
        return findAccessibleOrder(user, orderId)
                .flatMap(order -> checkTransition(user, order, target).thenReturn(order))
                .flatMap(order -> applyTransition(order, target))
                .delayUntil(this::notifyIfConfirmed);
    }

    private Mono<Void> checkTransition(AuthenticatedUser user, Order order, OrderStatus target) {
        if (!order.status().canTransitionTo(target)) {
            return Mono.error(new InvalidStatusTransitionException(order.status(), target));
        }
        boolean userCancellation = target == OrderStatus.CANCELLED && USER_CANCELLABLE.contains(order.status());
        if (!user.isAdmin() && !userCancellation) {
            return Mono.error(new AccessDeniedException("Only administrators can set status " + target));
        }
        return Mono.empty();
    }

    private Mono<OrderResponse> applyTransition(Order order, OrderStatus target) {
        return orderRepository.updateStatus(order.id(), order.status().name(), target.name())
                .flatMap(updated -> requireUpdated(updated, order.id()))
                .then(Mono.defer(() -> applyStockEffect(order, target)))
                .then(Mono.defer(() -> reload(order.id())))
                .as(transactionalOperator::transactional)
                .doOnNext(updated -> log.info("Order status changed orderId={} userId={} {} -> {}",
                        order.id(), order.userId(), order.status(), target));
    }

    private Mono<Void> applyStockEffect(Order order, OrderStatus target) {
        if (target == OrderStatus.CANCELLED && order.status().holdsReservedStock()) {
            return forEachItem(order.id(), item ->
                    inventoryService.release(item.productId(), item.quantity(), order.id()));
        }
        if (target == OrderStatus.COMPLETED) {
            return forEachItem(order.id(), item ->
                    inventoryService.consumeReserved(item.productId(), item.quantity(), order.id()));
        }
        return Mono.empty();
    }

    /**
     * La notificación es "best effort": si el servicio externo falla tras los reintentos, se
     * registra el fallo y el pedido sigue confirmado ({@code onErrorResume}).
     */
    private Mono<Void> notifyIfConfirmed(OrderResponse order) {
        if (order.status() != OrderStatus.CONFIRMED) {
            return Mono.empty();
        }
        log.info("Order confirmed orderId={} userId={}", order.id(), order.userId());
        return notificationClient.sendOrderConfirmed(OrderConfirmedNotification.from(order))
                .onErrorResume(ex -> {
                    log.warn("External notification failure orderId={} userId={} cause={}",
                            order.id(), order.userId(), ex.getMessage());
                    return Mono.empty();
                });
    }

    // --- Borrado ---

    /**
     * Solo se pueden borrar pedidos PENDING (se libera su stock) o CANCELLED.
     * El DELETE condicional por estado protege frente a un cambio de estado concurrente.
     */
    public Mono<Void> delete(AuthenticatedUser user, Long orderId) {
        return findAccessibleOrder(user, orderId)
                .filter(order -> DELETABLE.contains(order.status()))
                .switchIfEmpty(Mono.error(() -> new InvalidOrderException(
                        "Only PENDING or CANCELLED orders can be deleted")))
                .flatMap(this::deleteAndReleaseStock)
                .doOnSuccess(ignored -> log.info("Order deleted orderId={} by userId={}", orderId, user.id()));
    }

    private Mono<Void> deleteAndReleaseStock(Order order) {
        return orderItemRepository.findAllByOrderIdOrderByProductIdAsc(order.id()).collectList()
                .delayUntil(items -> orderRepository.deleteIfStatus(order.id(), order.status().name())
                        .flatMap(deleted -> requireUpdated(deleted, order.id())))
                .flatMapMany(Flux::fromIterable)
                .filter(item -> order.status().holdsReservedStock())
                .concatMap(item -> inventoryService.release(item.productId(), item.quantity(), order.id()))
                .then()
                .as(transactionalOperator::transactional);
    }

    // --- Utilidades ---

    private Mono<Order> findAccessibleOrder(AuthenticatedUser user, Long orderId) {
        Mono<Order> order = user.isAdmin()
                ? orderRepository.findById(orderId)
                : orderRepository.findByIdAndUserId(orderId, user.id());
        return order.switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)));
    }

    private Mono<OrderResponse> reload(Long orderId) {
        return orderRepository.findById(orderId)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(this::withItems);
    }

    private Mono<OrderResponse> withItems(Order order) {
        return orderItemRepository.findAllByOrderIdOrderByProductIdAsc(order.id())
                .collectList()
                .map(items -> OrderResponse.from(order, items));
    }

    private Mono<List<OrderResponse>> withItems(List<Order> orders) {
        if (orders.isEmpty()) {
            return Mono.just(List.of());
        }
        List<Long> ids = orders.stream().map(Order::id).toList();
        return orderItemRepository.findAllByOrderIdInOrderByProductIdAsc(ids)
                .collectMultimap(OrderItem::orderId)
                .map(itemsByOrder -> orders.stream()
                        .map(order -> OrderResponse.from(order, itemsOf(itemsByOrder, order.id())))
                        .toList());
    }

    private static List<OrderItem> itemsOf(Map<Long, Collection<OrderItem>> itemsByOrder, Long orderId) {
        return List.copyOf(itemsByOrder.getOrDefault(orderId, List.of()));
    }

    private Mono<Void> forEachItem(Long orderId, Function<OrderItem, Mono<Void>> action) {
        return orderItemRepository.findAllByOrderIdOrderByProductIdAsc(orderId)
                .concatMap(action)
                .then();
    }

    private static Mono<Void> requireUpdated(long affectedRows, Long orderId) {
        return affectedRows > 0 ? Mono.empty() : Mono.error(new OrderConflictException(orderId));
    }
}
