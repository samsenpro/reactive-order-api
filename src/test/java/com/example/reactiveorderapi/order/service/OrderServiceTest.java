package com.example.reactiveorderapi.order.service;

import com.example.reactiveorderapi.common.PageQuery;
import com.example.reactiveorderapi.exception.InvalidOrderException;
import com.example.reactiveorderapi.exception.InvalidStatusTransitionException;
import com.example.reactiveorderapi.exception.OrderConflictException;
import com.example.reactiveorderapi.exception.OrderNotFoundException;
import com.example.reactiveorderapi.inventory.service.InventoryService;
import com.example.reactiveorderapi.notification.NotificationClient;
import com.example.reactiveorderapi.notification.NotificationDeliveryException;
import com.example.reactiveorderapi.order.entity.Order;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import com.example.reactiveorderapi.order.repository.OrderItemRepository;
import com.example.reactiveorderapi.order.repository.OrderRepository;
import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.user.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.example.reactiveorderapi.support.Fixtures.item;
import static com.example.reactiveorderapi.support.Fixtures.order;
import static com.example.reactiveorderapi.support.Fixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

    private static final AuthenticatedUser OWNER = principal(1L, Role.USER);
    private static final AuthenticatedUser OTHER = principal(2L, Role.USER);
    private static final AuthenticatedUser ADMIN = principal(3L, Role.ADMIN);

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private NotificationClient notificationClient;
    @Mock
    private TransactionalOperator transactionalOperator;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, orderItemRepository, inventoryService,
                notificationClient, transactionalOperator);
        when(transactionalOperator.transactional(any(Mono.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(orderItemRepository.findAllByOrderIdOrderByProductIdAsc(10L))
                .thenReturn(Flux.just(item(10L, 1L, 2, "5.00"), item(10L, 2L, 1, "3.00")));
        when(inventoryService.release(anyLong(), anyInt(), anyLong())).thenReturn(Mono.empty());
        when(inventoryService.consumeReserved(anyLong(), anyInt(), anyLong())).thenReturn(Mono.empty());
    }

    @Test
    void ownerReadsOwnOrderWithItems() {
        when(orderRepository.findByIdAndUserId(10L, OWNER.id())).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)));

        StepVerifier.create(orderService.findById(OWNER, 10L))
                .assertNext(order -> assertThat(order.items()).hasSize(2))
                .verifyComplete();
    }

    @Test
    void foreignOrderLooksLikeMissingOrder() {
        when(orderRepository.findByIdAndUserId(10L, OTHER.id())).thenReturn(Mono.empty());

        StepVerifier.create(orderService.findById(OTHER, 10L))
                .expectError(OrderNotFoundException.class)
                .verify();
        verify(orderRepository, never()).findById(anyLong());
    }

    @Test
    void adminReadsAnyOrder() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)));

        StepVerifier.create(orderService.findById(ADMIN, 10L))
                .assertNext(order -> assertThat(order.userId()).isEqualTo(OWNER.id()))
                .verifyComplete();
    }

    @Test
    void pageLoadsItemsOfAllOrdersInOneQuery() {
        PageQuery page = new PageQuery(0, 20);
        when(orderRepository.findAllByUserId(OWNER.id(), page.toPageable()))
                .thenReturn(Flux.just(order(10L, OWNER.id(), OrderStatus.PENDING), order(11L, OWNER.id(), OrderStatus.PENDING)));
        when(orderRepository.countByUserId(OWNER.id())).thenReturn(Mono.just(2L));
        when(orderItemRepository.findAllByOrderIdInOrderByProductIdAsc(anyCollection()))
                .thenReturn(Flux.just(item(10L, 1L, 1, "1.00"), item(11L, 1L, 2, "1.00"), item(11L, 2L, 1, "1.00")));

        StepVerifier.create(orderService.findPage(OWNER, 999L, page))
                .assertNext(result -> {
                    assertThat(result.totalElements()).isEqualTo(2);
                    assertThat(result.content()).extracting(o -> o.items().size()).containsExactly(1, 2);
                })
                .verifyComplete();
        verify(orderRepository, never()).findAllBy(any());
    }

    @Test
    void userCancellationReleasesReservedStock() {
        givenAccessibleOrder(OrderStatus.PENDING, OrderStatus.CANCELLED);

        StepVerifier.create(orderService.changeStatus(OWNER, 10L, OrderStatus.CANCELLED))
                .assertNext(order -> assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED))
                .verifyComplete();

        verify(inventoryService).release(1L, 2, 10L);
        verify(inventoryService).release(2L, 1, 10L);
    }

    @Test
    void completingConsumesReservedStock() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PROCESSING)),
                Mono.just(order(10L, OWNER.id(), OrderStatus.COMPLETED)));
        when(orderRepository.updateStatus(10L, "PROCESSING", "COMPLETED")).thenReturn(Mono.just(1L));

        StepVerifier.create(orderService.changeStatus(ADMIN, 10L, OrderStatus.COMPLETED))
                .expectNextCount(1)
                .verifyComplete();

        verify(inventoryService).consumeReserved(1L, 2, 10L);
        verify(inventoryService, never()).release(anyLong(), anyInt(), anyLong());
    }

    @Test
    void userCannotConfirmOwnOrder() {
        when(orderRepository.findByIdAndUserId(10L, OWNER.id())).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)));

        StepVerifier.create(orderService.changeStatus(OWNER, 10L, OrderStatus.CONFIRMED))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(orderRepository, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    void invalidTransitionIsRejected() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.COMPLETED)));

        StepVerifier.create(orderService.changeStatus(ADMIN, 10L, OrderStatus.CANCELLED))
                .expectError(InvalidStatusTransitionException.class)
                .verify();
    }

    @Test
    void concurrentStatusChangeIsDetected() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)));
        when(orderRepository.updateStatus(10L, "PENDING", "CANCELLED")).thenReturn(Mono.just(0L));

        StepVerifier.create(orderService.changeStatus(ADMIN, 10L, OrderStatus.CANCELLED))
                .expectError(OrderConflictException.class)
                .verify();
        verify(inventoryService, never()).release(anyLong(), anyInt(), anyLong());
    }

    @Test
    void confirmationNotifiesAndSurvivesNotificationFailure() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)),
                Mono.just(order(10L, OWNER.id(), OrderStatus.CONFIRMED)));
        when(orderRepository.updateStatus(10L, "PENDING", "CONFIRMED")).thenReturn(Mono.just(1L));
        when(notificationClient.sendOrderConfirmed(any()))
                .thenReturn(Mono.error(new NotificationDeliveryException("down", new RuntimeException())));

        StepVerifier.create(orderService.changeStatus(ADMIN, 10L, OrderStatus.CONFIRMED))
                .assertNext(order -> assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED))
                .verifyComplete();
        verify(notificationClient).sendOrderConfirmed(any());
    }

    @Test
    void completedOrderCannotBeDeleted() {
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.COMPLETED)));

        StepVerifier.create(orderService.delete(ADMIN, 10L))
                .expectError(InvalidOrderException.class)
                .verify();
        verify(orderRepository, never()).deleteIfStatus(anyLong(), anyString());
    }

    @Test
    void deletingPendingOrderReleasesItsStock() {
        when(orderRepository.findByIdAndUserId(10L, OWNER.id())).thenReturn(Mono.just(order(10L, OWNER.id(), OrderStatus.PENDING)));
        when(orderRepository.deleteIfStatus(10L, "PENDING")).thenReturn(Mono.just(1L));

        StepVerifier.create(orderService.delete(OWNER, 10L)).verifyComplete();

        verify(inventoryService).release(1L, 2, 10L);
        verify(inventoryService).release(2L, 1, 10L);
    }

    private void givenAccessibleOrder(OrderStatus current, OrderStatus next) {
        Order before = order(10L, OWNER.id(), current);
        when(orderRepository.findByIdAndUserId(10L, OWNER.id())).thenReturn(Mono.just(before));
        when(orderRepository.findById(10L)).thenReturn(Mono.just(order(10L, OWNER.id(), next)));
        when(orderRepository.updateStatus(10L, current.name(), next.name())).thenReturn(Mono.just(1L));
    }
}
