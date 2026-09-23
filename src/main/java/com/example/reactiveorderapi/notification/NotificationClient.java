package com.example.reactiveorderapi.notification;

import com.example.reactiveorderapi.common.CorrelationId;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.util.concurrent.TimeoutException;

/**
 * Cliente HTTP reactivo del servicio de notificaciones (WebClient + Reactor Netty).
 * <ul>
 *     <li><b>Timeout</b>: cada intento se corta a los {@code app.notification.timeout}. El
 *     operador {@code timeout} cancela la suscripción y Reactor Netty cierra la conexión.</li>
 *     <li><b>Retry</b>: máximo {@code app.notification.max-retries} reintentos con backoff exponencial,
 *     solo ante fallos transitorios (timeout, error de conexión, 5xx y 429). Nunca ante otros
 *     4xx: repetir una petición incorrecta o no autorizada no la arregla.</li>
 * </ul>
 */
@Component
public class NotificationClient {

    private static final Logger log = LoggerFactory.getLogger(NotificationClient.class);
    private static final String NOTIFICATIONS_PATH = "/notifications";
    private static final int TOO_MANY_REQUESTS = 429;

    private final WebClient webClient;
    private final NotificationProperties properties;

    public NotificationClient(WebClient.Builder webClientBuilder, NotificationProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) properties.timeout().toMillis())
                .responseTimeout(properties.timeout());
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Mono<Void> sendOrderConfirmed(OrderConfirmedNotification notification) {
        return Mono.deferContextual(context -> webClient.post()
                        .uri(NOTIFICATIONS_PATH)
                        .header(CorrelationId.HEADER, context.getOrDefault(CorrelationId.CONTEXT_KEY, ""))
                        .bodyValue(notification)
                        .retrieve()
                        .toBodilessEntity()
                        // timeout por intento: se aplica antes de retryWhen
                        .timeout(properties.timeout()))
                .retryWhen(retryPolicy(notification.orderId()))
                .onErrorMap(ex -> !(ex instanceof NotificationDeliveryException),
                        ex -> new NotificationDeliveryException(
                                "Notification rejected for order " + notification.orderId(), ex))
                .doOnSuccess(ignored -> log.info("Notification sent orderId={}", notification.orderId()))
                .then();
    }

    private Retry retryPolicy(Long orderId) {
        return Retry.backoff(properties.maxRetries(), properties.retryBackoff())
                .filter(NotificationClient::isTransient)
                .doBeforeRetry(signal -> log.warn("Retrying notification orderId={} attempt={} cause={}",
                        orderId, signal.totalRetries() + 1, describe(signal.failure())))
                .onRetryExhaustedThrow((spec, signal) -> new NotificationDeliveryException(
                        "Notification failed after " + (signal.totalRetries() + 1) + " attempts for order " + orderId,
                        signal.failure()));
    }

    static boolean isTransient(Throwable error) {
        if (error instanceof TimeoutException || error instanceof WebClientRequestException) {
            return true;
        }
        if (error instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == TOO_MANY_REQUESTS;
        }
        return false;
    }

    private static String describe(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            return "HTTP " + response.getStatusCode().value();
        }
        return error.getClass().getSimpleName();
    }
}
