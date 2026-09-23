package com.example.reactiveorderapi.security.jwt;

import com.example.reactiveorderapi.security.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Emite y valida JWT HS256. Firmar y verificar son operaciones de CPU en memoria (sin E/S),
 * así que se pueden ejecutar en el event loop sin bloquearlo.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    private static final int MIN_SECRET_BYTES = 32;
    private static final String ROLE_CLAIM = "role";
    private static final String USER_ID_CLAIM = "uid";

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
        this.properties = properties;
        this.clock = clock;
    }

    public String generateToken(AuthenticatedUser user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(user.email())
                .issuer(properties.issuer())
                .claim(USER_ID_CLAIM, user.id())
                .claim(ROLE_CLAIM, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.expiration())))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Valida firma, emisor y expiración; devuelve el email (subject) si el token es válido. */
    public Optional<String> extractValidSubject(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.ofNullable(claims.getSubject()).filter(subject -> !subject.isBlank());
        } catch (JwtException | IllegalArgumentException ex) {
            // Nunca se registra el token, solo el tipo de fallo.
            log.debug("Rejected JWT: {}", ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public long getExpirationSeconds() {
        return properties.expiration();
    }
}
