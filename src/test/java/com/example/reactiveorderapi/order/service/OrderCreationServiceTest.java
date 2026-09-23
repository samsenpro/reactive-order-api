package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.exception.InsufficientStockException;
import com.example.reactiveorderapi.exception.InvalidOrderException;
import com.example.reactiveorderapi.exception.ProductInactiveException;
import com.example.reactiveorderapi.exception.ProductNotFoundException;
import com.example.reactiveorderapi.exception.UserNotFoundException;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.order.dto.CreateOrderRequest;
import com.example.reactiveorderapi.order.dto.OrderItemRequest;
import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderItem;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import com.example.reactiveorderapi.order.repository.OrderItemRepository;
import com.example.reactiveorderapi.order.repository.OrderRepository;
import com.example.reactiveorderapi.product.service.ProductService;
import com.example.reactiveorderapi.user.entity.Role;
import com.example.reactiveorderapi.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.reactivestreams.Publisher;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.reactiveorderapi.support.Fixtures.NOW;
import static com.example.reactiveorderapi.support.Fixtures.inventory;
import static com.example.reactiveorderapi.support.Fixtures.product;
import static com.example.reactiveorderapi.support.Fixtures.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderCreationServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserService userService;
    @Mock
    private ProductService productService;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private TransactionalOperator transactionalOperator;

    private OrderCreationService service;
    private final AtomicBoolean transactionUsed = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        service = new OrderCreationService(userService, productService, inventoryService, orderRepository,
                orderItemRepository, transactionalOperator, Clock.fixed(NOW, ZoneOffset.UTC));

        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> {
            transactionUsed.set(true);
            return invocation.getArgument(0);
        });
        when(userService.requireActiveUser(USER_ID)).thenReturn(Mono.just(user(USER_ID, Role.USER)));
        when(productService.findAllByIds(anyCollection()))
                .thenReturn(Flux.just(product(1L, "10.00", true), product(2L, "2.50", true)));
        when(inventoryService.findAllByProductIds(anyCollection()))
                .thenReturn(Flux.just(inventory(1L, 5, 0), inventory(2L, 5, 0)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            return Mono.just(new Order(100L, order.userId(), order.status(), order.totalAmount(),
                    order.createdAt(), order.updatedAt()));
        });
        when(orderItemRepository.saveAll(any(Publisher.class))).thenAnswer(invocation ->
                Flux.from(invocation.<Publisher<OrderItem>>getArgument(0)));
        when(inventoryService.reserve(anyLong(), anyInt(), eq(100L))).thenReturn(Mono.empty());
    }

    @Test
    void createsPendingOrderWithBackendPricesInsideATransaction() {
        StepVerifier.create(service.create(USER_ID, request(item(2L, 4), item(1L, 2))))
                .assertNext(order -> {
                    assertThat(order.id()).isEqualTo(100L);
                    assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
                    assertThat(order.userId()).isEqualTo(USER_ID);
                    assertThat(order.totalAmount()).isEqualByComparingTo("30.00");
                    assertThat(order.items()).extracting(item -> item.productId()).containsExactly(1L, 2L);
                })
                .verifyComplete();

        assertThat(transactionUsed).isTrue();
        // Reservas en orden de productId (evita deadlocks)
        InOrder reservations = inOrder(inventoryService);
        reservations.verify(inventoryService).reserve(1L, 2, 100L);
        reservations.verify(inventoryService).reserve(2L, 4, 100L);
    }

    @Test
    void failedReservationPropagatesErrorSoTheTransactionRollsBack() {
        when(inventoryService.reserve(2L, 4, 100L)).thenReturn(Mono.error(new InsufficientStockException(2L, 4)));

        StepVerifier.create(service.create(USER_ID, request(item(1L, 2), item(2L, 4))))
                .expectError(InsufficientStockException.class)
                .verify();
        assertThat(transactionUsed).isTrue();
    }

    @Test
    void missingProductFailsBeforeWriting() {
        when(productService.findAllByIds(anyCollection())).thenReturn(Flux.just(product(1L, "10.00", true)));

        StepVerifier.create(service.create(USER_ID, request(item(1L, 1), item(2L, 1))))
                .expectError(ProductNotFoundException.class)
                .verify();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void inactiveProductFailsBeforeWriting() {
        when(productService.findAllByIds(anyCollection())).thenReturn(Flux.just(product(1L, "10.00", false)));

        StepVerifier.create(service.create(USER_ID, request(item(1L, 1))))
                .expectError(ProductInactiveException.class)
                .verify();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void unknownOrDisabledUserFails() {
        when(userService.requireActiveUser(USER_ID)).thenReturn(Mono.error(new UserNotFoundException(USER_ID)));

        StepVerifier.create(service.create(USER_ID, request(item(1L, 1))))
                .expectError(UserNotFoundException.class)
                .verify();
        verify(productService, never()).findAllByIds(anyCollection());
    }

    @Test
    void invalidRequestFailsAsAnErrorSignalNotAThrownException() {
        StepVerifier.create(service.create(USER_ID, new CreateOrderRequest(List.of())))
                .expectError(InvalidOrderException.class)
                .verify();
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    private static OrderItemRequest item(Long productId, int quantity) {
        return new OrderItemRequest(productId, quantity);
    }
}
