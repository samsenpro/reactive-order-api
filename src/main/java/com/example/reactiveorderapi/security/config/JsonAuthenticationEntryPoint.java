package com.example.reactiveorderapi.security.config;

import com.example.reactiveorderapi.exception.ApiErrorWriter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 401 en JSON para peticiones sin token o con un token inválido, expirado o de una cuenta bloqueada.
 */
@Component
public class JsonAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ApiErrorWriter errorWriter;

    public JsonAuthenticationEntryPoint(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
