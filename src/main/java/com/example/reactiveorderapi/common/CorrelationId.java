package com.example.reactiveorderapi.common;

import org.springframework.web.server.ServerWebExchange;

/**
 * Constantes del correlation ID compartidas por el filtro, los logs, los errores y WebClient.
 */
public final class CorrelationId {

    public static final String HEADER = "X-Correlation-ID";
    /** Clave en el Context de Reactor y en el MDC de SLF4J. */
    public static final String CONTEXT_KEY = "correlationId";
    /** Atributo del exchange, para leerlo fuera de la cadena reactiva (p. ej. en el error handler). */
    public static final String EXCHANGE_ATTRIBUTE = CorrelationId.class.getName();

    private CorrelationId() {
    }

    public static String from(ServerWebExchange exchange) {
        return exchange.getAttribute(EXCHANGE_ATTRIBUTE);
    }
}
