package com.example.reactiveorderapi.security.jwt;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret     clave HMAC; al menos 32 bytes (HS256)
 * @param expiration validez del access token en segundos
 * @param issuer     emisor que se firma y se exige al validar
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Positive long expiration,
        @NotBlank String issuer
) {

    @Override
    public String toString() {
        return "JwtProperties{expiration=" + expiration + ", issuer=" + issuer + "}";
    }
}
