package com.example.reactiveorderapi.security.jwt;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Extrae el token del header {@code Authorization: Bearer <token>} del {@link ServerWebExchange}.
 * Si no hay header Bearer devuelve vacío: la petición sigue como anónima y la regla de
 * autorización decide (401 si el endpoint está protegido).
 */
public class BearerTokenConverter implements ServerAuthenticationConverter {

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        return Mono.justOrEmpty(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .filter(header -> header.startsWith(BEARER_PREFIX))
                .map(header -> header.substring(BEARER_PREFIX.length()).trim())
                .filter(token -> !token.isEmpty())
                .map(token -> UsernamePasswordAuthenticationToken.unauthenticated(null, token));
    }
}
