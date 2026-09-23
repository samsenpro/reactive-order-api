package com.example.reactiveorderapi.security;

import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.math.BigDecimal;
import java.util.Map;

class SecurityIntegrationTest extends AbstractIntegrationTest {

    @Test
    void requestWithoutJwtIsUnauthorized() {
        client.get().uri("/api/v1/orders")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectHeader().valueEquals(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .expectBody()
                .jsonPath("$.status").isEqualTo(401)
                .jsonPath("$.error").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.path").isEqualTo("/api/v1/orders");
    }

    @Test
    void invalidOrTamperedJwtIsUnauthorized() {
        String token = newUser().token();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("AA") ? "BB" : "AA");

        client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer("not.a.jwt"))
                .exchange()
                .expectStatus().isUnauthorized();
        client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(tampered))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void userCanUseAllowedEndpoints() {
        String user = newUser().token();

        client.get().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void userCannotUseAdminEndpoints() {
        String user = newUser().token();

        client.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .bodyValue(Map.of("name", "X", "sku", randomSku(), "price", BigDecimal.ONE))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.error").isEqualTo("FORBIDDEN");
        client.patch().uri("/api/v1/products/1/deactivate")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .exchange()
                .expectStatus().isForbidden();
        client.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void userAccessesOwnOrderButNotForeignOne() {
        ProductResponse product = createProduct(adminToken(), "Book", "12.00", 10);
        TestUser owner = newUser();
        TestUser other = newUser();
        OrderResponse order = createOrder(owner.token(), orderOf(product.id(), 1));

        client.get().uri("/api/v1/orders/{id}", order.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(owner.token()))
                .exchange()
                .expectStatus().isOk();

        client.get().uri("/api/v1/orders/{id}", order.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                .exchange()
                .expectStatus().isNotFound();
        changeStatus(other.token(), order.id(), "CANCELLED").expectStatus().isNotFound();
        client.delete().uri("/api/v1/orders/{id}", order.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void userOnlyListsOwnOrdersEvenWhenFilteringByAnotherUser() {
        ProductResponse product = createProduct(adminToken(), "Book", "12.00", 10);
        TestUser owner = newUser();
        TestUser other = newUser();
        createOrder(owner.token(), orderOf(product.id(), 1));

        client.get().uri("/api/v1/orders?userId={id}", owner.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(other.token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void adminAccessesAnyOrder() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Book", "12.00", 10);
        TestUser owner = newUser();
        OrderResponse order = createOrder(owner.token(), orderOf(product.id(), 1));

        client.get().uri("/api/v1/orders/{id}", order.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.userId").isEqualTo(owner.id().intValue());

        client.get().uri("/api/v1/orders?userId={id}", owner.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(1);
    }

    @Test
    void publicAndProtectedOperationalEndpoints() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("UP");

        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.components.securitySchemes.bearerAuth.scheme").isEqualTo("bearer");

        client.get().uri("/actuator/metrics")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .exchange()
                .expectStatus().isOk();
        client.get().uri("/actuator/info")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.app.name").isEqualTo("Reactive Order API");
    }
}
