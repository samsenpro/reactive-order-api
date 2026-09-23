package com.example.reactiveorderapi.security.config;

import com.example.reactiveorderapi.exception.ApiErrorWriter;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 403 en JSON para usuarios autenticados sin el rol necesario.
 */
@Component
public class JsonAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ApiErrorWriter errorWriter;

    public JsonAccessDeniedHandler(ApiErrorWriter errorWriter) {
        this.errorWriter = errorWriter;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return errorWriter.write(exchange, HttpStatus.FORBIDDEN, "Access denied");
    }
}
