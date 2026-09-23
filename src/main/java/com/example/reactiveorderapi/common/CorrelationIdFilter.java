package com.example.reactiveorderapi.common;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Asigna un correlation ID a cada petición.
 * <ul>
 *     <li>Si el cliente envía {@code X-Correlation-ID} válido, se conserva; si no, se genera uno.</li>
 *     <li>Se devuelve en la cabecera de la respuesta.</li>
 *     <li>Se guarda en el <b>Context de Reactor</b> (no en un ThreadLocal, porque en WebFlux la
 *     petición puede saltar de hilo). Con {@code spring.reactor.context-propagation=auto},
 *     Micrometer lo copia al MDC en cada operador y aparece en todos los logs.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    /** Solo se aceptan IDs cortos y seguros: evita inyección en logs y cabeceras enormes. */
    private static final Pattern VALID_ID = Pattern.compile("^[A-Za-z0-9._-]{1,64}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = resolve(exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));

        exchange.getAttributes().put(CorrelationId.EXCHANGE_ATTRIBUTE, correlationId);
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);

        return chain.filter(exchange)
                .contextWrite(Context.of(CorrelationId.CONTEXT_KEY, correlationId));
    }

    private static String resolve(String candidate) {
        return candidate != null && VALID_ID.matcher(candidate).matches()
                ? candidate
                : UUID.randomUUID().toString();
    }
}
