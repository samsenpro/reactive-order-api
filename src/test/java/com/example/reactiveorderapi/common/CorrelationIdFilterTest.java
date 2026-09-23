package com.example.reactiveorderapi.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void keepsValidIncomingIdAndPutsItInTheReactorContext() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/orders").header(CorrelationId.HEADER, "abc-123"));
        AtomicReference<String> seenInContext = new AtomicReference<>();
        WebFilterChain chain = ignored -> Mono.deferContextual(context -> {
            seenInContext.set(context.get(CorrelationId.CONTEXT_KEY));
            return Mono.empty();
        });

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(seenInContext.get()).isEqualTo("abc-123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).isEqualTo("abc-123");
        assertThat(CorrelationId.from(exchange)).isEqualTo("abc-123");
    }

    @Test
    void generatesIdWhenMissingOrUnsafe() {
        MockServerWebExchange missing = MockServerWebExchange.from(MockServerHttpRequest.get("/"));
        MockServerWebExchange unsafe = MockServerWebExchange.from(
                MockServerHttpRequest.get("/").header(CorrelationId.HEADER, "bad\nvalue"));

        StepVerifier.create(filter.filter(missing, ignored -> Mono.empty())).verifyComplete();
        StepVerifier.create(filter.filter(unsafe, ignored -> Mono.empty())).verifyComplete();

        assertThat(missing.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).matches("[0-9a-f-]{36}");
        assertThat(unsafe.getResponse().getHeaders().getFirst(CorrelationId.HEADER)).matches("[0-9a-f-]{36}");
    }
}
