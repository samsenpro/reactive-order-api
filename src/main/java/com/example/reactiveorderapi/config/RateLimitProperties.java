package com.example.reactiveorderapi.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param authMaxRequests peticiones permitidas por IP a /api/v1/auth/** en cada ventana
 * @param authWindow      duración de la ventana
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        @Positive int authMaxRequests,
        @NotNull Duration authWindow
) {
}
