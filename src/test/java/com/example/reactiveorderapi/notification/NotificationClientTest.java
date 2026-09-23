package com.example.reactiveorderapi.notification;

import com.example.reactiveorderapi.common.CorrelationId;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebClient contra un servidor HTTP real en local (MockWebServer): permite simular
 * respuestas lentas, errores 5xx y secuencias fallo → éxito para probar timeout y retry.
 */
class NotificationClientTest {

    private static final Duration TIMEOUT = Duration.ofMillis(300);
    private static final OrderConfirmedNotification NOTIFICATION =
            new OrderConfirmedNotification(OrderConfirmedNotification.TYPE, 42L, 7L, new BigDecimal("99.90"));

    private MockWebServer server;
    private NotificationClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/").toString();
        client = new NotificationClient(WebClient.builder(),
                new NotificationProperties(baseUrl.substring(0, baseUrl.length() - 1), TIMEOUT, 2, Duration.ofMillis(10)));
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void successfulResponseCompletes() throws InterruptedException {
        server.enqueue(new MockResponse().setResponseCode(202));

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION)
                        .contextWrite(Context.of(CorrelationId.CONTEXT_KEY, "cid-42")))
                .verifyComplete();

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/notifications");
        assertThat(request.getHeader(CorrelationId.HEADER)).isEqualTo("cid-42");
        assertThat(request.getBody().readUtf8()).contains("\"orderId\":42", "\"type\":\"ORDER_CONFIRMED\"");
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void recoversWhenARetrySucceedsAfterA500() {
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(202));

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION)).verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void persistent500FailsAfterBoundedRetries() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(503));
        }

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotificationDeliveryException.class)
                            .hasMessageContaining("after 3 attempts");
                    assertThat(error.getCause()).isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
                })
                .verify(Duration.ofSeconds(5));

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void slowServerTimesOutAndIsRetriedBeforeFailing() {
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setResponseCode(202).setHeadersDelay(1, TimeUnit.SECONDS));
        }

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotificationDeliveryException.class);
                    assertThat(NotificationClient.isTransient(error.getCause())).isTrue();
                })
                .verify(Duration.ofSeconds(5));

        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void timeoutThenSuccessCompletes() {
        server.enqueue(new MockResponse().setResponseCode(202).setHeadersDelay(1, TimeUnit.SECONDS));
        server.enqueue(new MockResponse().setResponseCode(202));

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION)).verifyComplete();

        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    void clientErrorsAreNotRetried() {
        server.enqueue(new MockResponse().setResponseCode(400));

        StepVerifier.create(client.sendOrderConfirmed(NOTIFICATION))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(NotificationDeliveryException.class);
                    assertThat(error.getCause()).isInstanceOf(WebClientResponseException.BadRequest.class);
                })
                .verify(Duration.ofSeconds(5));

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void transientErrorClassification() {
        assertThat(NotificationClient.isTransient(new TimeoutException())).isTrue();
        assertThat(NotificationClient.isTransient(WebClientResponseException.create(500, "", null, null, null))).isTrue();
        assertThat(NotificationClient.isTransient(WebClientResponseException.create(429, "", null, null, null))).isTrue();
        assertThat(NotificationClient.isTransient(WebClientResponseException.create(401, "", null, null, null))).isFalse();
        assertThat(NotificationClient.isTransient(WebClientResponseException.create(404, "", null, null, null))).isFalse();
        assertThat(NotificationClient.isTransient(new IllegalArgumentException())).isFalse();
    }
}
