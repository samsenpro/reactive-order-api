package com.example.reactiveorderapi.product;

import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminCreatesProductWithItsInventory() {
        String admin = adminToken();
        String sku = "kb-" + UUID.randomUUID().toString().substring(0, 8);

        ProductResponse created = client.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .bodyValue(Map.of("name", "Keyboard", "sku", sku, "price", new BigDecimal("89.90"), "initialStock", 7))
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().value(HttpHeaders.LOCATION, location -> assertThat(location).startsWith("/api/v1/products/"))
                .expectBody(ProductResponse.class).returnResult().getResponseBody();

        assertThat(created.sku()).isEqualTo(sku.toUpperCase());
        assertThat(created.price()).isEqualByComparingTo("89.90");
        assertThat(created.active()).isTrue();
        assertThat(inventoryOf(admin, created.id()).availableQuantity()).isEqualTo(7);
    }

    @Test
    void productCanBeReadUpdatedAndDeleted() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Mouse", "19.99", 3);

        client.get().uri("/api/v1/products/{id}", product.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(newUser().token()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.name").isEqualTo("Mouse");

        client.put().uri("/api/v1/products/{id}", product.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .bodyValue(Map.of("name", "Wireless Mouse", "sku", product.sku(), "price", new BigDecimal("24.50")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Wireless Mouse")
                .jsonPath("$.price").isEqualTo(24.5);

        client.delete().uri("/api/v1/products/{id}", product.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isNoContent();

        client.get().uri("/api/v1/products/{id}", product.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.error").isEqualTo("NOT_FOUND")
                .jsonPath("$.message").isEqualTo("Product not found: " + product.id())
                .jsonPath("$.path").isEqualTo("/api/v1/products/" + product.id());
    }

    @Test
    void duplicatedSkuIsRejected() {
        String admin = adminToken();
        ProductResponse existing = createProduct(admin, "Monitor", "199.00", 1);

        client.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .bodyValue(Map.of("name", "Other", "sku", existing.sku().toLowerCase(), "price", BigDecimal.TEN))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void invalidProductIsRejected() {
        client.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .bodyValue(Map.of("name", "", "sku", "x", "price", BigDecimal.ZERO))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody().jsonPath("$.errors.length()").isEqualTo(3);
    }

    @Test
    void searchAppliesFiltersAndPagination() {
        String admin = adminToken();
        String tag = UUID.randomUUID().toString().substring(0, 8);
        createProduct(admin, "Keyboard " + tag + " basic", "20.00", 1);
        createProduct(admin, "Keyboard " + tag + " pro", "120.00", 1);
        ProductResponse inactive = createProduct(admin, "Keyboard " + tag + " old", "60.00", 1);
        client.patch().uri("/api/v1/products/{id}/deactivate", inactive.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.active").isEqualTo(false);

        client.get().uri(uri -> uri.path("/api/v1/products")
                        .queryParam("name", tag.toUpperCase())
                        .queryParam("active", true)
                        .queryParam("minPrice", "50")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(1)
                .jsonPath("$.content[0].name").isEqualTo("Keyboard " + tag + " pro");

        client.get().uri(uri -> uri.path("/api/v1/products")
                        .queryParam("name", tag).queryParam("page", 1).queryParam("size", 2).build())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalElements").isEqualTo(3)
                .jsonPath("$.totalPages").isEqualTo(2)
                .jsonPath("$.content.length()").isEqualTo(1);
    }

    @Test
    void likeWildcardsInFilterAreTreatedLiterally() {
        client.get().uri(uri -> uri.path("/api/v1/products").queryParam("name", "%").build())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .exchange()
                .expectStatus().isOk()
                .expectBody().jsonPath("$.totalElements").isEqualTo(0);
    }

    @Test
    void invalidPriceRangeIsBadRequest() {
        client.get().uri(uri -> uri.path("/api/v1/products").queryParam("minPrice", 50).queryParam("maxPrice", 10).build())
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken()))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void streamEmitsActiveProductsAsNdjson() {
        String admin = adminToken();
        ProductResponse active = createProduct(admin, "Streamed", "5.00", 1);
        ProductResponse inactive = createProduct(admin, "Hidden", "5.00", 1);
        client.patch().uri("/api/v1/products/{id}/deactivate", inactive.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isOk();

        var stream = client.get().uri("/api/v1/products/stream")
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .accept(MediaType.APPLICATION_NDJSON)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_NDJSON)
                .returnResult(ProductResponse.class)
                .getResponseBody();

        StepVerifier.create(stream.map(ProductResponse::id).collectList())
                .assertNext(ids -> assertThat(ids).contains(active.id()).doesNotContain(inactive.id()).isSorted())
                .verifyComplete();
    }

    @Test
    void productWithOrdersCannotBeDeleted() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Sold", "10.00", 5);
        createOrder(newUser().token(), orderOf(product.id(), 1));

        client.delete().uri("/api/v1/products/{id}", product.id())
                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
