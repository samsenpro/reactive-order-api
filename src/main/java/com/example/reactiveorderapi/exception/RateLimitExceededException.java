package com.example.reactiveorderapi.exception;

import org.springframework.http.HttpStatus;

import java.time.Duration;

/**
 * Demasiadas peticiones en la ventana de tiempo (HTTP 429). Incluye cuándo reintentar.
 */
public class RateLimitExceededException extends ApiException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
