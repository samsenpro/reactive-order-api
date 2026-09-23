package com.example.reactiveorderapi.inventory;

import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminAddsAndRemovesStock() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Cable", "3.00", 10);

        adjust(admin, product.id(), "add", 5)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.availableQuantity").isEqualTo(15);

        adjust(admin, product.id(), "remove", 12)
                .expectStatus().isOk()
                .expectBody().jsonPath("$.availableQuantity").isEqualTo(3);
    }

    @Test
    void removingMoreThanAvailableIsRejectedAndStockUnchanged() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Cable", "3.00", 2);

        adjust(admin, product.id(), "remove", 3)
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.message").isEqualTo("Insufficient stock for product " + product.id() + " (requested 3)");

        assertThat(inventoryOf(admin, product.id()).availableQuantity()).isEqualTo(2);
    }

    @Test
    void unknownProductIsNotFound() {
        String admin = adminToken();

        client.get().uri("/api/v1/inventory/{id}", Long.MAX_VALUE)
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isNotFound();
        adjust(admin, Long.MAX_VALUE, "add", 1).expectStatus().isNotFound();
        adjust(admin, Long.MAX_VALUE, "remove", 1).expectStatus().isNotFound();
    }

    @Test
    void invalidQuantityIsRejected() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Cable", "3.00", 2);

        adjust(admin, product.id(), "add", 0).expectStatus().isBadRequest();
        adjust(admin, product.id(), "remove", -1).expectStatus().isBadRequest();
    }

    @Test
    void userCanReadButNotModifyStock() {
        ProductResponse product = createProduct(adminToken(), "Cable", "3.00", 2);
        String user = newUser().token();

        assertThat(inventoryOf(user, product.id()).availableQuantity()).isEqualTo(2);
        adjust(user, product.id(), "add", 1).expectStatus().isForbidden();
    }

    private org.springframework.test.web.reactive.server.WebTestClient.ResponseSpec adjust(
            String token, Long productId, String operation, int quantity) {
        return client.patch().uri("/api/v1/inventory/{id}/{op}", productId, operation)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .bodyValue(Map.of("quantity", quantity))
                .exchange();
    }
}
