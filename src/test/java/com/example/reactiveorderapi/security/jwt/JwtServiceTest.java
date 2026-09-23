package com.example.reactiveorderapi.security.jwt;

import com.example.reactiveorderapi.security.AuthenticatedUser;
import com.example.reactiveorderapi.user.entity.Role;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.example.reactiveorderapi.support.Fixtures.principal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "test-secret-key-with-at-least-32-bytes!!";
    private static final String ISSUER = "reactive-order-api";
    private static final Instant NOW = Instant.parse("2026-09-23T10:00:00Z");
    private static final AuthenticatedUser USER = principal(1L, Role.USER);

    private final JwtService jwtService = service(SECRET, ISSUER, NOW);

    @Test
    void generatedTokenCarriesEmailAsSubject() {
        assertThat(jwtService.extractValidSubject(jwtService.generateToken(USER))).contains(USER.email());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwtService.generateToken(USER);

        assertThat(service(SECRET, ISSUER, NOW.plus(Duration.ofSeconds(3601))).extractValidSubject(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        String foreign = service("another-secret-key-with-at-least-32-bytes", ISSUER, NOW).generateToken(USER);

        assertThat(jwtService.extractValidSubject(foreign)).isEmpty();
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        String foreign = service(SECRET, "someone-else", NOW).generateToken(USER);

        assertThat(jwtService.extractValidSubject(foreign)).isEmpty();
    }

    @Test
    void tamperedOrGarbageTokensAreRejected() {
        String[] parts = jwtService.generateToken(USER).split("\\.");

        assertThat(jwtService.extractValidSubject(parts[0] + "." + parts[1] + "x." + parts[2])).isEmpty();
        assertThat(jwtService.extractValidSubject("garbage")).isEmpty();
    }

    @Test
    void shortSecretIsRejectedAtStartup() {
        assertThatThrownBy(() -> service("short", ISSUER, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    private static JwtService service(String secret, String issuer, Instant now) {
        return new JwtService(new JwtProperties(secret, 3600, issuer), Clock.fixed(now, ZoneOffset.UTC));
    }
}
