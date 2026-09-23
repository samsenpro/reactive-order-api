package com.example.reactiveorderapi.order;

import com.example.reactiveorderapi.common.CorrelationId;
import com.example.reactiveorderapi.inventory.dto.InventoryResponse;
import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.order.entity.OrderStatus;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrderIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createOrderCalculatesPricesInBackendAndReservesStock() {
        String admin = adminToken();
        ProductResponse keyboard = createProduct(admin, "Keyboard", "89.90", 10);
        ProductResponse mouse = createProduct(admin, "Mouse", "19.99", 5);
        TestUser user = newUser();

        // El cliente intenta imponer precio y total: el backend los ignora
        Map<String, Object> body = Map.of(
                "totalAmount", 1,
                "items", List.of(
                        Map.of("productId", keyboard.id(), "quantity", 2, "unitPrice", 0.01),
                        Map.of("productId", mouse.id(), "quantity", 1)));

        OrderResponse order = createOrder(user.token(), body);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.userId()).isEqualTo(user.id());
        assertThat(order.totalAmount()).isEqualByComparingTo("199.79");
        assertThat(order.items()).hasSize(2);
        assertThat(order.items().getFirst().unitPrice()).isEqualByComparingTo("89.90");
        assertThat(order.items().getFirst().subtotal()).isEqualByComparingTo("179.80");

        assertStock(admin, keyboard.id(), 8, 2);
        assertStock(admin, mouse.id(), 4, 1);
    }

    @Test
    void repeatedProductLinesAreMerged() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Pen", "1.50", 10);

        OrderResponse order = createOrder(newUser().token(), orderOf(product.id(), 2, product.id(), 3));

        assertThat(order.items()).singleElement()
                .satisfies(item -> assertThat(item.quantity()).isEqualTo(5));
        assertThat(order.totalAmount()).isEqualByComparingTo("7.50");
    }

    @Test
    void insufficientStockRejectsOrderWithoutSideEffects() {
        String admin = adminToken();
        ProductResponse plenty = createProduct(admin, "Plenty", "1.00", 100);
        ProductResponse scarce = createProduct(admin, "Scarce", "1.00", 1);
        TestUser user = newUser();

        postOrder(user.token(), orderOf(plenty.id(), 5, scarce.id(), 2))
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("Insufficient stock for product " + scarce.id() + " (requested 2)");

        assertStock(admin, plenty.id(), 100, 0);
        assertStock(admin, scarce.id(), 1, 0);
        client.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                .exchange()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void inactiveOrMissingProductsAreRejected() {
        String admin = adminToken();
        ProductResponse inactive = createProduct(admin, "Old", "1.00", 10);
        client.patch().uri("/api/v1/products/{id}/deactivate", inactive.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk();
        String user = newUser().token();

        postOrder(user, orderOf(inactive.id(), 1)).expectStatus().isEqualTo(422);
        postOrder(user, orderOf(Long.MAX_VALUE, 1)).expectStatus().isNotFound();
    }

    @Test
    void invalidOrderPayloadIsRejected() {
        String user = newUser().token();

        postOrder(user, Map.of("items", List.of())).expectStatus().isBadRequest();
        postOrder(user, orderOf(1, 0))
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors[0].field").isEqualTo("items[0].quantity");
    }

    @Test
    void userListsAndReadsOwnOrders() {
        ProductResponse product = createProduct(adminToken(), "Pen", "2.00", 10);
        TestUser user = newUser();
        OrderResponse first = createOrder(user.token(), orderOf(product.id(), 1));
        OrderResponse second = createOrder(user.token(), orderOf(product.id(), 2));

        client.get().uri("/api/v1/orders/{id}", first.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.items[0].quantity").isEqualTo(1);

        client.get().uri("/api/v1/orders?size=1")
                .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(2)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.content[0].id").isEqualTo(second.id().intValue())
                .jsonPath("$.content[0].items[0].quantity").isEqualTo(2);
    }

    @Test
    void fullLifecycleConfirmsNotifiesAndConsumesStock() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Lamp", "30.00", 5);
        TestUser user = newUser();
        OrderResponse order = createOrder(user.token(), orderOf(product.id(), 2));

        client.patch().uri("/api/v1/orders/{id}/status", order.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .header(CorrelationId.HEADER, "confirm-flow-1")
                .bodyValue(Map.of("status", "CONFIRMED"))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CONFIRMED");

        RecordedRequest notification = NOTIFICATIONS.poll(2_000);
        assertThat(notification).isNotNull();
        assertThat(notification.getPath()).isEqualTo("/notifications");
        assertThat(notification.getHeader(CorrelationId.HEADER)).isEqualTo("confirm-flow-1");
        assertThat(notification.getBody().readUtf8())
                .contains("\"type\":\"ORDER_CONFIRMED\"", "\"orderId\":" + order.id());

        changeStatus(admin, order.id(), "PROCESSING").expectStatus().isOk();
        changeStatus(admin, order.id(), "COMPLETED")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("COMPLETED");

        assertStock(admin, product.id(), 3, 0);
    }

    @Test
    void notificationFailureDoesNotUndoConfirmation() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Lamp", "30.00", 5);
        OrderResponse order = createOrder(newUser().token(), orderOf(product.id(), 1));
        NOTIFICATIONS.respondWith(500);

        changeStatus(admin, order.id(), "CONFIRMED")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CONFIRMED");

        // 1 intento + 2 reintentos
        assertThat(NOTIFICATIONS.poll(2_000)).isNotNull();
        assertThat(NOTIFICATIONS.poll(2_000)).isNotNull();
        assertThat(NOTIFICATIONS.poll(2_000)).isNotNull();
        assertThat(NOTIFICATIONS.poll(300)).isNull();
    }

    @Test
    void cancellingReleasesReservedStock() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Chair", "45.00", 4);
        TestUser user = newUser();
        OrderResponse order = createOrder(user.token(), orderOf(product.id(), 3));
        assertStock(admin, product.id(), 1, 3);

        changeStatus(user.token(), order.id(), "CANCELLED")
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("CANCELLED");

        assertStock(admin, product.id(), 4, 0);
    }

    @Test
    void invalidTransitionsAreRejected() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Chair", "45.00", 4);
        TestUser user = newUser();
        OrderResponse order = createOrder(user.token(), orderOf(product.id(), 1));

        changeStatus(admin, order.id(), "COMPLETED").expectStatus().isEqualTo(422);
        changeStatus(user.token(), order.id(), "CONFIRMED").expectStatus().isForbidden();
        changeStatus(user.token(), order.id(), "CANCELLED").expectStatus().isOk();
        changeStatus(admin, order.id(), "CONFIRMED").expectStatus().isEqualTo(422);
    }

    @Test
    void deletingPendingOrderReleasesStockAndCompletedCannotBeDeleted() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Desk", "150.00", 3);
        TestUser user = newUser();
        OrderResponse pending = createOrder(user.token(), orderOf(product.id(), 2));

        client.delete().uri("/api/v1/orders/{id}", pending.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(user.token()))
                .exchange()
                .expectStatus().isNoContent();
        assertStock(admin, product.id(), 3, 0);

        OrderResponse completed = createOrder(user.token(), orderOf(product.id(), 1));
        changeStatus(admin, completed.id(), "CONFIRMED").expectStatus().isOk();
        changeStatus(admin, completed.id(), "PROCESSING").expectStatus().isOk();
        changeStatus(admin, completed.id(), "COMPLETED").expectStatus().isOk();

        client.delete().uri("/api/v1/orders/{id}", completed.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    private void assertStock(String token, Long productId, int available, int reserved) {
        InventoryResponse inventory = inventoryOf(token, productId);
        assertThat(inventory.availableQuantity()).as("available").isEqualTo(available);
        assertThat(inventory.reservedQuantity()).as("reserved").isEqualTo(reserved);
    }
}
