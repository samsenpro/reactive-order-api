package com.example.reactiveorderapi.order;

import com.example.reactiveorderapi.inventory.dto.InventoryResponse;
import com.example.reactiveorderapi.product.dto.ProductResponse;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pedidos realmente simultáneos contra PostgreSQL: se lanzan N peticiones HTTP en paralelo
 * con WebClient y se comprueba que nunca se vende más stock del que hay y que ningún pedido
 * fallido deja datos a medias.
 */
class StockConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private DatabaseClient databaseClient;

    @Test
    void twoUsersCompetingForTheLastUnitsNeverOversell() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Limited", "10.00", 5);
        List<String> tokens = List.of(newUser().token(), newUser().token());

        Map<Integer, Long> statuses = placeConcurrently(tokens, token -> orderOf(product.id(), 4));

        assertThat(statuses).containsEntry(201, 1L).containsEntry(409, 1L);
        InventoryResponse stock = inventoryOf(admin, product.id());
        assertThat(stock.availableQuantity()).isEqualTo(1);
        assertThat(stock.reservedQuantity()).isEqualTo(4);
    }

    @Test
    void manyConcurrentOrdersSellExactlyTheAvailableStock() {
        String admin = adminToken();
        ProductResponse product = createProduct(admin, "Popular", "1.00", 10);
        List<String> tokens = Flux.range(0, 25).map(i -> newUser().token()).collectList().block();

        Map<Integer, Long> statuses = placeConcurrently(tokens, token -> orderOf(product.id(), 1));

        assertThat(statuses).containsEntry(201, 10L).containsEntry(409, 15L);
        InventoryResponse stock = inventoryOf(admin, product.id());
        assertThat(stock.availableQuantity()).isZero();
        assertThat(stock.reservedQuantity()).isEqualTo(10);
    }

    /**
     * Cada pedido reserva primero A (abundante) y después B (1 unidad). Los perdedores ya han
     * insertado el pedido, sus items y la reserva de A cuando falla B: la transacción reactiva
     * debe deshacerlo todo.
     */
    @Test
    void failedReservationRollsBackOrderItemsAndPreviousReservations() {
        String admin = adminToken();
        ProductResponse plenty = createProduct(admin, "A-plenty", "1.00", 100);
        ProductResponse scarce = createProduct(admin, "B-scarce", "1.00", 1);
        List<String> tokens = Flux.range(0, 10).map(i -> newUser().token()).collectList().block();

        Map<Integer, Long> statuses = placeConcurrently(tokens, token -> orderOf(plenty.id(), 1, scarce.id(), 1));

        assertThat(statuses).containsEntry(201, 1L).containsEntry(409, 9L);
        InventoryResponse a = inventoryOf(admin, plenty.id());
        assertThat(a.availableQuantity()).isEqualTo(99);
        assertThat(a.reservedQuantity()).isEqualTo(1);
        assertThat(inventoryOf(admin, scarce.id()).availableQuantity()).isZero();
        assertThat(countOrderItemsFor(plenty.id())).isEqualTo(1);
    }

    private Map<Integer, Long> placeConcurrently(List<String> tokens,
                                                 Function<String, Map<String, Object>> bodyFor) {
        WebClient webClient = WebClient.create("http://localhost:" + port);
        return Flux.fromIterable(tokens)
                .flatMap(token -> webClient.post().uri("/api/v1/orders")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .bodyValue(bodyFor.apply(token))
                        .exchangeToMono(response -> response.releaseBody()
                                .thenReturn(response.statusCode().value())), tokens.size())
                .collectList()
                .block(Duration.ofSeconds(30))
                .stream()
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
    }

    private long countOrderItemsFor(Long productId) {
        return databaseClient.sql("SELECT COUNT(*) FROM order_items WHERE product_id = :productId")
                .bind("productId", productId)
                .map(row -> row.get(0, Long.class))
                .one()
                .block(Duration.ofSeconds(5));
    }
}
