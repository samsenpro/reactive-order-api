package com.example.reactiveorderapi.exception;

import com.example.reactiveorderapi.common.ApiError;
import com.example.reactiveorderapi.common.ApiError.FieldViolation;
import com.example.reactiveorderapi.common.CorrelationId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Escribe un {@link ApiError} como JSON en la respuesta, de forma no bloqueante.
 * Lo comparten el manejador global de errores y los handlers 401/403 de seguridad.
 */
@Component
public class ApiErrorWriter {

    private static final String FALLBACK_BODY = "{\"status\":500,\"error\":\"INTERNAL_SERVER_ERROR\"}";

    private final ObjectMapper objectMapper;

    public ApiErrorWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message) {
        return write(exchange, status, message, List.of());
    }

    public Mono<Void> write(ServerWebExchange exchange, HttpStatus status, String message,
                            List<FieldViolation> violations) {
        ApiError body = ApiError.of(status, message, exchange.getRequest().getPath().value(),
                CorrelationId.from(exchange), violations);

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serialize(body));
        return response.writeWith(Mono.just(buffer));
    }

    private byte[] serialize(ApiError body) {
        try {
            return objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException ex) {
            return FALLBACK_BODY.getBytes(StandardCharsets.UTF_8);
        }
    }
}
