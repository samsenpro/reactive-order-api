package com.example.reactiveorderapi.security;

import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

/**
 * Contexto propio con un límite bajo para comprobar el 429.
 */
@TestPropertySource(properties = "app.rate-limit.auth-max-requests=3")
class AuthRateLimitIntegrationTest extends AbstractIntegrationTest {

    @Test
    void tooManyAuthRequestsReturn429WithRetryAfter() {
        Map<String, String> body = Map.of("email", randomEmail(), "password", PASSWORD);
        for (int i = 0; i < 3; i++) {
            client.post().uri("/api/v1/auth/login").bodyValue(body).exchange().expectStatus().isUnauthorized();
        }

        client.post().uri("/api/v1/auth/login")
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists(HttpHeaders.RETRY_AFTER)
                .expectBody().jsonPath("$.error").isEqualTo("TOO_MANY_REQUESTS");
    }

    @Override
    protected String adminToken() {
        throw new UnsupportedOperationException("Not used: every auth request counts against the limit");
    }
}
