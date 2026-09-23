package com.example.reactiveorderapi.support;

import com.example.reactiveorderapi.auth.dto.AuthResponse;
import com.example.reactiveorderapi.config.AdminInitializer;
import com.example.reactiveorderapi.inventory.dto.InventoryResponse;
import com.example.reactiveorderapi.order.dto.OrderResponse;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.user.dto.UserResponse;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Base de los tests de integración:
 * <ul>
 *     <li>PostgreSQL real en un contenedor (Testcontainers), compartido por todas las clases.
 *     {@code @ServiceConnection} configura a la vez R2DBC (la aplicación) y JDBC (Flyway).</li>
 *     <li>La aplicación arranca en un puerto aleatorio y se prueba por HTTP con {@link WebTestClient}.</li>
 *     <li>Un servidor HTTP falso hace de servicio de notificaciones.</li>
 * </ul>
 * Cada test crea sus propios usuarios y productos con identificadores aleatorios; no hace falta limpiar la BD.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.jwt.secret=integration-test-secret-key-with-at-least-32-bytes",
        "app.admin.email=" + AbstractIntegrationTest.ADMIN_EMAIL,
        "app.admin.password=" + AbstractIntegrationTest.ADMIN_PASSWORD,
        "app.rate-limit.auth-max-requests=100000",
        "app.notification.timeout=500ms",
        "app.notification.max-retries=2",
        "app.notification.retry-backoff=20ms"
})
@AutoConfigureWebTestClient(timeout = "30s")
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@test.local";
    protected static final String ADMIN_PASSWORD = "AdminPassword1";
    protected static final String PASSWORD = "Password123";

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    protected static final NotificationServerStub NOTIFICATIONS = new NotificationServerStub();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void notificationProperties(DynamicPropertyRegistry registry) {
        registry.add("app.notification.base-url", NOTIFICATIONS::baseUrl);
    }

    @Autowired
    protected WebTestClient client;

    @Autowired
    private AdminInitializer adminInitializer;

    @BeforeEach
    void waitForAdminSeed() {
        adminInitializer.completion().block(Duration.ofSeconds(10));
        NOTIFICATIONS.reset();
    }

    // --- Usuarios ---

    protected static String randomEmail() {
        return "user-" + UUID.randomUUID() + "@test.local";
    }

    protected TestUser newUser() {
        String email = randomEmail();
        UserResponse user = client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("name", "Test User", "email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(UserResponse.class).returnResult().getResponseBody();
        return new TestUser(user.id(), email, login(email, PASSWORD));
    }

    protected String login(String email, String password) {
        return client.post().uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", email, "password", password))
                .exchange()
                .expectStatus().isOk()
                .expectBody(AuthResponse.class).returnResult().getResponseBody()
                .accessToken();
    }

    protected String adminToken() {
        return login(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    // --- Catálogo ---

    protected static String randomSku() {
        return "SKU-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    protected ProductResponse createProduct(String adminToken, String name, String price, int stock) {
        return client.post().uri("/api/v1/products")
                .header(HttpHeaders.AUTHORIZATION, bearer(adminToken))
                .bodyValue(Map.of("name", name, "sku", randomSku(), "price", new BigDecimal(price), "initialStock", stock))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class).returnResult().getResponseBody();
    }

    protected InventoryResponse inventoryOf(String token, Long productId) {
        return client.get().uri("/api/v1/inventory/{id}", productId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .exchange()
                .expectStatus().isOk()
                .expectBody(InventoryResponse.class).returnResult().getResponseBody();
    }

    // --- Pedidos ---

    protected static Map<String, Object> orderOf(Object... productIdAndQuantity) {
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        for (int i = 0; i < productIdAndQuantity.length; i += 2) {
            items.add(Map.of("productId", productIdAndQuantity[i], "quantity", productIdAndQuantity[i + 1]));
        }
        return Map.of("items", items);
    }

    protected WebTestClient.ResponseSpec postOrder(String token, Map<String, Object> body) {
        return client.post().uri("/api/v1/orders")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .bodyValue(body)
                .exchange();
    }

    protected OrderResponse createOrder(String token, Map<String, Object> body) {
        return postOrder(token, body)
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class).returnResult().getResponseBody();
    }

    protected WebTestClient.ResponseSpec changeStatus(String token, Long orderId, String status) {
        return client.patch().uri("/api/v1/orders/{id}/status", orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .bodyValue(Map.of("status", status))
                .exchange();
    }

    protected record TestUser(Long id, String email, String token) {
    }
}
