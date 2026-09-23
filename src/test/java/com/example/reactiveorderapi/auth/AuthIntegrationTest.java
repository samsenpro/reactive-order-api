package com.example.reactiveorderapi.auth;

import com.example.reactiveorderapi.common.CorrelationId;
import com.example.reactiveorderapi.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.matchesPattern;

class AuthIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerCreatesUserWithoutExposingPassword() {
        String email = randomEmail();

        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("name", "Jane", "email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.email").isEqualTo(email)
                .jsonPath("$.role").isEqualTo("USER")
                .jsonPath("$.password").doesNotExist();
    }

    @Test
    void duplicatedEmailIsRejectedCaseInsensitively() {
        String email = randomEmail();
        newUserWith(email);

        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("name", "Jane", "email", email.toUpperCase(), "password", PASSWORD))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody().jsonPath("$.error").isEqualTo("CONFLICT");
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() {
        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("name", "", "email", "not-an-email", "password", "weak"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Validation failed")
                .jsonPath("$.errors[*].field").value(hasItems("name", "email", "password"));
    }

    @Test
    void loginReturnsBearerToken() {
        String email = randomEmail();
        newUserWith(email);

        client.post().uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isNotEmpty()
                .jsonPath("$.tokenType").isEqualTo("Bearer")
                .jsonPath("$.expiresIn").isEqualTo(3600);
    }

    @Test
    void loginErrorsAreGenericForWrongPasswordAndUnknownEmail() {
        String email = randomEmail();
        newUserWith(email);

        client.post().uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", email, "password", "WrongPassword1"))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid email or password");

        client.post().uri("/api/v1/auth/login")
                .bodyValue(Map.of("email", randomEmail(), "password", PASSWORD))
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.message").isEqualTo("Invalid email or password");
    }

    @Test
    void correlationIdIsPreservedWhenValidAndGeneratedOtherwise() {
        client.post().uri("/api/v1/auth/login")
                .header(CorrelationId.HEADER, "abc-123")
                .bodyValue(Map.of("email", randomEmail(), "password", PASSWORD))
                .exchange()
                .expectHeader().valueEquals(CorrelationId.HEADER, "abc-123")
                .expectBody().jsonPath("$.correlationId").isEqualTo("abc-123");

        client.post().uri("/api/v1/auth/login")
                .header(CorrelationId.HEADER, "invalid value with spaces")
                .bodyValue(Map.of("email", randomEmail(), "password", PASSWORD))
                .exchange()
                .expectHeader().value(CorrelationId.HEADER, matchesPattern("^[0-9a-f-]{36}$"));
    }

    private void newUserWith(String email) {
        client.post().uri("/api/v1/auth/register")
                .bodyValue(Map.of("name", "Jane", "email", email, "password", PASSWORD))
                .exchange()
                .expectStatus().isCreated();
    }
}
