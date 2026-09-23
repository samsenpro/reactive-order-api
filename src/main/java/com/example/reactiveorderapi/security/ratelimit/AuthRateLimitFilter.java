package com.example.reactiveorderapi.security.ratelimit;

import com.example.reactiveorderapi.config.RateLimitProperties;
import com.example.reactiveorderapi.exception.RateLimitExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Clock;

/**
 * Limita las peticiones por IP a los endpoints de autenticación (registro y login) para
 * frenar ataques de fuerza bruta. Al superarse el límite responde 429 con {@code Retry-After}.
 * <p>
 * Usa la IP de la conexión y no {@code X-Forwarded-For}, que el cliente puede falsificar.
 * Detrás de un proxy de confianza se configuraría el forwarding de Spring.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthRateLimitFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);
    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";
    private static final String UNKNOWN_CLIENT = "unknown";

    private final FixedWindowRateLimiter rateLimiter;

    public AuthRateLimitFilter(RateLimitProperties properties, Clock clock) {
        this.rateLimiter = new FixedWindowRateLimiter(properties.authMaxRequests(), properties.authWindow(), clock);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getPath().value().startsWith(AUTH_PATH_PREFIX)) {
            return chain.filter(exchange);
        }
        String client = clientKey(request);
        return rateLimiter.tryAcquire(client)
                .map(retryAfter -> {
                    log.warn("Auth rate limit exceeded path={}", request.getPath().value());
                    return Mono.<Void>error(new RateLimitExceededException(retryAfter));
                })
                .orElseGet(() -> chain.filter(exchange));
    }

    private static String clientKey(ServerHttpRequest request) {
        InetSocketAddress address = request.getRemoteAddress();
        return address == null || address.getAddress() == null
                ? UNKNOWN_CLIENT
                : address.getAddress().getHostAddress();
    }
}
