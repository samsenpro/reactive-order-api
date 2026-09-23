package com.example.reactiveorderapi.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param baseUrl      URL del servicio de notificaciones
 * @param timeout      tiempo máximo de cada intento
 * @param maxRetries   reintentos tras el primer intento (acotado; nunca infinito)
 * @param retryBackoff espera inicial entre reintentos; crece exponencialmente con jitter
 */
@Validated
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(
        @NotBlank String baseUrl,
        @NotNull Duration timeout,
        @PositiveOrZero int maxRetries,
        @NotNull Duration retryBackoff
) {
}
