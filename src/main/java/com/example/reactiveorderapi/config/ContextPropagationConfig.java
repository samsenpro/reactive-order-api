package com.example.reactiveorderapi.config;

import com.example.reactiveorderapi.common.CorrelationId;
import io.micrometer.context.ContextRegistry;
import org.slf4j.MDC;
import org.springframework.context.annotation.Configuration;

/**
 * Enlaza la clave {@code correlationId} del Context de Reactor con el MDC de SLF4J.
 * Cada vez que un operador se ejecuta, la propagación automática de Reactor restaura el
 * valor en el MDC del hilo actual y lo limpia al terminar.
 */
@Configuration
public class ContextPropagationConfig {

    public ContextPropagationConfig() {
        ContextRegistry.getInstance().registerThreadLocalAccessor(
                CorrelationId.CONTEXT_KEY,
                () -> MDC.get(CorrelationId.CONTEXT_KEY),
                value -> MDC.put(CorrelationId.CONTEXT_KEY, value),
                () -> MDC.remove(CorrelationId.CONTEXT_KEY));
    }
}
